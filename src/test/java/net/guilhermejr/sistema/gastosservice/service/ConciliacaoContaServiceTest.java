package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.TransacaoBancoMapper;
import net.guilhermejr.sistema.gastosservice.api.request.ConciliarContaRequest;
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
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

public class ConciliacaoContaServiceTest {

    private LancamentoService lancamentoService;
    private LancamentoRepository lancamentoRepository;
    private MovimentacaoRepository movimentacaoRepository;
    private TransacaoBancoRepository transacaoBancoRepository;
    private ConciliacaoContaService service;
    private Conta conta;
    private TransacaoBanco transacao;

    @BeforeEach
    public void preparar() {

        PluggyClient pluggyClient = Mockito.mock(PluggyClient.class);
        Mockito.when(pluggyClient.liberadoPara(Mockito.any())).thenReturn(true);
        ContaService contaService = Mockito.mock(ContaService.class);
        lancamentoService = Mockito.mock(LancamentoService.class);
        lancamentoRepository = Mockito.mock(LancamentoRepository.class);
        movimentacaoRepository = Mockito.mock(MovimentacaoRepository.class);
        transacaoBancoRepository = Mockito.mock(TransacaoBancoRepository.class);
        Clock clock = Clock.fixed(LocalDate.of(2026, 10, 30).atStartOfDay(ZoneId.of("America/Bahia")).toInstant(), ZoneId.of("America/Bahia"));
        service = new ConciliacaoContaService(pluggyClient, contaService, Mockito.mock(SaldoService.class), Mockito.mock(RecorrenciaService.class),
                lancamentoService, lancamentoRepository, movimentacaoRepository, transacaoBancoRepository, Mockito.mock(TransacaoBancoMapper.class),
                Mockito.mock(ConverteStringUtil.class), Mockito.mock(AuthenticationCurrentUserService.class, Answers.RETURNS_DEEP_STUBS), clock);

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
