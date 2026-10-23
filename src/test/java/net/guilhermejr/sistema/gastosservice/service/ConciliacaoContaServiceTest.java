package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.TransacaoBancoMapper;
import net.guilhermejr.sistema.gastosservice.api.request.ConciliarContaRequest;
import net.guilhermejr.sistema.gastosservice.api.request.ConfirmarConciliacaoContaRequest;
import net.guilhermejr.sistema.gastosservice.api.request.CriarDoBancoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.LancamentoRequest;
import net.guilhermejr.sistema.gastosservice.api.request.RendimentoContaRequest;
import net.guilhermejr.sistema.gastosservice.api.response.ConciliacaoContaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.LancamentoResponse;
import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.SituacaoTransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoMovimentacao;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.MovimentacaoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.TransacaoBancoRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public class ConciliacaoContaServiceTest {

    private LancamentoService lancamentoService;
    private LancamentoRepository lancamentoRepository;
    private MovimentacaoRepository movimentacaoRepository;
    private TransacaoBancoRepository transacaoBancoRepository;
    private ConciliacaoContaService service;
    private Conta conta;
    private TransacaoBanco transacao;
    private SaldoService saldoService;

    @BeforeEach
    public void preparar() {

        PluggyClient pluggyClient = Mockito.mock(PluggyClient.class);
        saldoService = Mockito.mock(SaldoService.class);
        Mockito.when(pluggyClient.liberadoPara(Mockito.any())).thenReturn(true);
        ContaService contaService = Mockito.mock(ContaService.class);
        lancamentoService = Mockito.mock(LancamentoService.class);
        lancamentoRepository = Mockito.mock(LancamentoRepository.class);
        movimentacaoRepository = Mockito.mock(MovimentacaoRepository.class);
        transacaoBancoRepository = Mockito.mock(TransacaoBancoRepository.class);
        ConverteStringUtil converteStringUtil = Mockito.mock(ConverteStringUtil.class);
        Mockito.when(converteStringUtil.toYearMonth(2026, 10)).thenReturn(YearMonth.of(2026, 10));
        Clock clock = Clock.fixed(LocalDate.of(2026, 10, 30).atStartOfDay(ZoneId.of("America/Bahia")).toInstant(), ZoneId.of("America/Bahia"));
        service = new ConciliacaoContaService(pluggyClient, contaService, saldoService, Mockito.mock(RecorrenciaService.class),
                lancamentoService, lancamentoRepository, movimentacaoRepository, transacaoBancoRepository, Mockito.mock(TransacaoBancoMapper.class),
                converteStringUtil, Mockito.mock(AuthenticationCurrentUserService.class, Answers.RETURNS_DEEP_STUBS), clock);

        conta = new Conta();
        conta.setId(2L);
        conta.setBancoContaId("cc1");
        conta.setBancoInicio(LocalDate.of(2026, 10, 11));
        Mockito.when(contaService.contaDoUsuario(2L)).thenReturn(conta);

        transacao = new TransacaoBanco();
        transacao.setId(7L);
        transacao.setConta(conta);
        transacao.setTipo(TipoLancamento.D);
        transacao.setValor(new BigDecimal("2395.26"));
        transacao.setData(LocalDate.of(2026, 10, 28));
        transacao.setSituacao(SituacaoTransacaoBanco.A_REVISAR);
        Mockito.when(transacaoBancoRepository.findByIdAndConta(7L, conta)).thenReturn(Optional.of(transacao));

    }

    @Test
    @DisplayName("Conciliar com um lançamento o dá como realizado, na data do banco")
    public void conciliar_lancamento() {

        Lancamento carro = new Lancamento();
        carro.setId(30L);
        carro.setConta(conta);
        carro.setTipo(TipoLancamento.D);
        carro.setValor(new BigDecimal("2395.26"));
        carro.setData(LocalDate.of(2026, 10, 30));
        carro.setRealizado(false);
        Mockito.when(lancamentoService.lancamentoDoUsuario(30L)).thenReturn(carro);

        service.conciliar(2L, 7L, ConciliarContaRequest.builder().lancamentoId(30L).build());

        Assertions.assertTrue(carro.getRealizado());
        Assertions.assertEquals(LocalDate.of(2026, 10, 28), carro.getData());
        Assertions.assertSame(carro, transacao.getLancamento());
        Assertions.assertEquals(SituacaoTransacaoBanco.CONCILIADA, transacao.getSituacao());
        Mockito.verify(lancamentoRepository).save(carro);

    }

    private Lancamento seguro() {
        Lancamento seguro = new Lancamento();
        seguro.setId(31L);
        seguro.setConta(conta);
        seguro.setTipo(TipoLancamento.D);
        seguro.setValor(new BigDecimal("2395.26"));
        seguro.setData(LocalDate.of(2026, 11, 2));
        seguro.setRealizado(false);
        Mockito.when(lancamentoService.lancamentoDoUsuario(31L)).thenReturn(seguro);
        return seguro;
    }

    @Test
    @DisplayName("Confirmar uma transação ainda pendente (agendamento) só liga: o lançamento não é realizado")
    public void confirmar_pendente() {

        transacao.setPendente(true);
        Lancamento seguro = seguro();
        ConfirmarConciliacaoContaRequest.Item item = new ConfirmarConciliacaoContaRequest.Item();
        item.setTransacaoId(7L);
        item.setLancamentoId(31L);
        ConfirmarConciliacaoContaRequest request = ConfirmarConciliacaoContaRequest.builder().itens(List.of(item)).build();

        service.confirmar(2L, 2026, 10, request);

        Assertions.assertFalse(seguro.getRealizado());
        Assertions.assertEquals(LocalDate.of(2026, 11, 2), seguro.getData());
        Assertions.assertSame(seguro, transacao.getLancamento());
        Assertions.assertEquals(SituacaoTransacaoBanco.CONCILIADA, transacao.getSituacao());
        Mockito.verify(lancamentoRepository, Mockito.never()).save(Mockito.any());

    }

    @Test
    @DisplayName("Transação com data futura também só liga, mesmo sem vir pendente")
    public void conciliar_data_futura() {

        transacao.setPendente(false);
        transacao.setData(LocalDate.of(2026, 10, 31));
        Lancamento seguro = seguro();

        service.conciliar(2L, 7L, ConciliarContaRequest.builder().lancamentoId(31L).build());

        Assertions.assertFalse(seguro.getRealizado());
        Assertions.assertSame(seguro, transacao.getLancamento());

    }

    @Test
    @DisplayName("Corrigir o valor de uma pendente muda só o valor, sem realizar nem mudar a data")
    public void corrigir_valor_pendente() {

        transacao.setPendente(true);
        Lancamento seguro = seguro();
        seguro.setValor(new BigDecimal("2300.00"));
        seguro.setCategoria(new net.guilhermejr.sistema.gastosservice.domain.entity.Categoria());
        seguro.getCategoria().setId(5L);

        service.conciliar(2L, 7L, ConciliarContaRequest.builder().lancamentoId(31L).corrigirValor(true).build());

        ArgumentCaptor<LancamentoRequest> requisicao = ArgumentCaptor.forClass(LancamentoRequest.class);
        Mockito.verify(lancamentoService).atualizar(Mockito.eq(31L), requisicao.capture(), Mockito.any());
        Assertions.assertEquals("2395,26", requisicao.getValue().getValor());
        Assertions.assertEquals("02/11/2026", requisicao.getValue().getData());
        Assertions.assertFalse(requisicao.getValue().getRealizado());

    }

    @Test
    @DisplayName("Criar a partir de uma pendente cria o lançamento sem realizar")
    public void criar_pendente() {

        transacao.setPendente(true);
        LancamentoResponse criado = new LancamentoResponse();
        criado.setId(32L);
        Mockito.when(lancamentoService.inserir(Mockito.any())).thenReturn(List.of(criado));
        Mockito.when(lancamentoService.lancamentoDoUsuario(32L)).thenReturn(new Lancamento());

        service.criar(2L, 7L, CriarDoBancoRequest.builder().descricao("Seguro").categoriaId(5L).build());

        ArgumentCaptor<LancamentoRequest> requisicao = ArgumentCaptor.forClass(LancamentoRequest.class);
        Mockito.verify(lancamentoService).inserir(requisicao.capture());
        Assertions.assertFalse(requisicao.getValue().getRealizado());
        Assertions.assertEquals(SituacaoTransacaoBanco.IMPORTADA, transacao.getSituacao());

    }

    @Test
    @DisplayName("Tudo conciliado e o banco acima: a diferença pode virar rendimento, lançado hoje e fora da conciliação")
    public void lancar_rendimento() {

        conta.setBancoSaldo(new BigDecimal("829.25"));
        Mockito.when(saldoService.saldo(conta)).thenReturn(new BigDecimal("829.18"));
        LancamentoResponse criado = new LancamentoResponse();
        criado.setId(50L);
        Mockito.when(lancamentoService.inserir(Mockito.any())).thenReturn(List.of(criado));
        Lancamento rendimento = new Lancamento();
        Mockito.when(lancamentoService.lancamentoDoUsuario(50L)).thenReturn(rendimento);

        service.lancarRendimento(2L, RendimentoContaRequest.builder().categoriaId(91L).build());

        ArgumentCaptor<LancamentoRequest> requisicao = ArgumentCaptor.forClass(LancamentoRequest.class);
        Mockito.verify(lancamentoService).inserir(requisicao.capture());
        Assertions.assertEquals(TipoLancamento.R, requisicao.getValue().getTipo());
        Assertions.assertEquals("0,07", requisicao.getValue().getValor());
        Assertions.assertEquals("30/10/2026", requisicao.getValue().getData());
        Assertions.assertTrue(requisicao.getValue().getRealizado());
        Assertions.assertTrue(rendimento.getAjusteBanco());
        Mockito.verify(lancamentoRepository).save(rendimento);

    }

    @Test
    @DisplayName("Com transação do banco a conciliar até hoje, a diferença não é oferecida como rendimento")
    public void rendimento_com_pendencia() {

        conta.setBancoSaldo(new BigDecimal("829.25"));
        Mockito.when(saldoService.saldo(conta)).thenReturn(new BigDecimal("829.18"));
        Mockito.when(transacaoBancoRepository.datasAConciliar(Mockito.eq(conta), Mockito.any())).thenReturn(List.of(LocalDate.of(2026, 10, 28)));

        ConciliacaoContaResponse resposta = service.conciliacao(2L, 2026, 10);
        Assertions.assertNull(resposta.getRendimento());
        RendimentoContaRequest request = RendimentoContaRequest.builder().categoriaId(91L).build();
        Assertions.assertThrows(ExceptionDefault.class, () -> service.lancarRendimento(2L, request));
        Mockito.verify(lancamentoService, Mockito.never()).inserir(Mockito.any());

    }

    @Test
    @DisplayName("Banco abaixo do sistema não é rendimento")
    public void rendimento_banco_abaixo() {

        conta.setBancoSaldo(new BigDecimal("829.00"));
        Mockito.when(saldoService.saldo(conta)).thenReturn(new BigDecimal("829.18"));

        Assertions.assertNull(service.conciliacao(2L, 2026, 10).getRendimento());

    }

    @Test
    @DisplayName("Movimentação com outro valor não é conciliada")
    public void movimentacao_com_outro_valor() {

        Movimentacao pagamento = new Movimentacao();
        pagamento.setId(40L);
        pagamento.setTipo(TipoMovimentacao.PAGAMENTO_FATURA);
        pagamento.setContaOrigem(conta);
        pagamento.setValor(new BigDecimal("2000.00"));
        pagamento.setData(LocalDate.of(2026, 10, 28));
        Mockito.when(movimentacaoRepository.findByIdAndUsuario(Mockito.eq(40L), Mockito.any())).thenReturn(Optional.of(pagamento));

        ConciliarContaRequest request = ConciliarContaRequest.builder().movimentacaoId(40L).build();
        Assertions.assertThrows(ExceptionDefault.class, () -> service.conciliar(2L, 7L, request));
        Assertions.assertEquals(SituacaoTransacaoBanco.A_REVISAR, transacao.getSituacao());

    }

    @Test
    @DisplayName("Conciliar com uma movimentação só liga as duas")
    public void conciliar_movimentacao() {

        Movimentacao pagamento = new Movimentacao();
        pagamento.setId(40L);
        pagamento.setTipo(TipoMovimentacao.PAGAMENTO_FATURA);
        pagamento.setContaOrigem(conta);
        pagamento.setValor(new BigDecimal("2395.26"));
        pagamento.setData(LocalDate.of(2026, 10, 28));
        Mockito.when(movimentacaoRepository.findByIdAndUsuario(Mockito.eq(40L), Mockito.any())).thenReturn(Optional.of(pagamento));

        service.conciliar(2L, 7L, ConciliarContaRequest.builder().movimentacaoId(40L).build());

        Assertions.assertSame(pagamento, transacao.getMovimentacao());
        Assertions.assertNull(transacao.getLancamento());
        Assertions.assertEquals(SituacaoTransacaoBanco.CONCILIADA, transacao.getSituacao());

    }

    @Test
    @DisplayName("Desfazer solta a movimentação e volta a transação para revisar")
    public void desfazer() {

        transacao.setSituacao(SituacaoTransacaoBanco.CONCILIADA);
        transacao.setMovimentacao(new Movimentacao());

        service.desfazer(2L, 7L);

        Assertions.assertNull(transacao.getMovimentacao());
        Assertions.assertEquals(SituacaoTransacaoBanco.A_REVISAR, transacao.getSituacao());

    }

}
