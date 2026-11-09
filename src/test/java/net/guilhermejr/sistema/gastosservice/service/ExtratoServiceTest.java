package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.sistema.gastosservice.api.response.ExtratoContaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.LinhaExtratoResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoMovimentacao;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.MovimentacaoRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public class ExtratoServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();

    private LancamentoRepository lancamentoRepository;
    private MovimentacaoRepository movimentacaoRepository;
    private ConfiguracaoService configuracaoService;
    private ExtratoService service;
    private Conta conta;

    @BeforeEach
    public void preparar() {

        ContaService contaService = Mockito.mock(ContaService.class);
        configuracaoService = Mockito.mock(ConfiguracaoService.class);
        lancamentoRepository = Mockito.mock(LancamentoRepository.class);
        movimentacaoRepository = Mockito.mock(MovimentacaoRepository.class);
        Clock clock = Clock.fixed(LocalDate.of(2026, 11, 8).atStartOfDay(ZoneId.of("America/Bahia")).toInstant(), ZoneId.of("America/Bahia"));
        service = new ExtratoService(contaService, configuracaoService, lancamentoRepository, movimentacaoRepository,
                new ConverteStringUtil(), clock);

        conta = new Conta();
        conta.setId(2L);
        conta.setNome("Itaú");
        conta.setUsuario(USUARIO);
        conta.setSaldoInicial(new BigDecimal("1000.00"));
        Mockito.when(contaService.contaDoUsuario(2L)).thenReturn(conta);
        Mockito.when(movimentacaoRepository.somarDaContaAntesDe(Mockito.eq(conta), Mockito.any())).thenReturn(BigDecimal.ZERO);

    }

    private static Lancamento lancamento(long id, TipoLancamento tipo, String valor, LocalDate data, LocalDateTime criado) {

        Lancamento lancamento = new Lancamento();
        lancamento.setId(id);
        lancamento.setTipo(tipo);
        lancamento.setDescricao("Lançamento " + id);
        lancamento.setValor(new BigDecimal(valor));
        lancamento.setData(data);
        lancamento.setRealizado(true);
        lancamento.setCriado(criado);
        return lancamento;

    }

    private Movimentacao pagamentoFatura(long id, String valor, LocalDate data, LocalDateTime criado) {

        Cartao cartao = new Cartao();
        cartao.setNome("Personnalité");
        Movimentacao movimentacao = new Movimentacao();
        movimentacao.setId(id);
        movimentacao.setTipo(TipoMovimentacao.PAGAMENTO_FATURA);
        movimentacao.setValor(new BigDecimal(valor));
        movimentacao.setData(data);
        movimentacao.setContaOrigem(conta);
        movimentacao.setCartao(cartao);
        movimentacao.setCriado(criado);
        return movimentacao;

    }

    @Test
    @DisplayName("Saldo anterior soma o saldo inicial ao que é de antes do início, e cada linha leva o saldo depois dela")
    public void saldoAnteriorEcorrido() {

        LocalDate inicio = LocalDate.of(2026, 11, 1);
        LocalDate fim = LocalDate.of(2026, 11, 30);
        Mockito.when(lancamentoRepository.somarRealizadosDaContaAntesDe(conta, inicio)).thenReturn(List.of(
                new Object[]{TipoLancamento.R, new BigDecimal("500.00")},
                new Object[]{TipoLancamento.D, new BigDecimal("200.00")}));
        Mockito.when(movimentacaoRepository.somarDaContaAntesDe(conta, inicio)).thenReturn(new BigDecimal("-100.00"));
        Mockito.when(lancamentoRepository.findAllByContaAndRealizadoTrueAndDataBetweenOrderByDataAscIdAsc(conta, inicio, fim)).thenReturn(List.of(
                lancamento(1, TipoLancamento.R, "3000.00", LocalDate.of(2026, 11, 5), LocalDateTime.of(2026, 11, 5, 9, 0)),
                lancamento(2, TipoLancamento.D, "50.00", LocalDate.of(2026, 11, 10), LocalDateTime.of(2026, 11, 10, 12, 0))));
        Mockito.when(movimentacaoRepository.findDaContaNoPeriodo(USUARIO, conta, inicio, fim)).thenReturn(List.of(
                pagamentoFatura(9, "1200.00", LocalDate.of(2026, 11, 10), LocalDateTime.of(2026, 11, 10, 8, 0))));

        ExtratoContaResponse extrato = service.extrato(2L, "01/11/2026", "30/11/2026");

        Assertions.assertEquals(0, new BigDecimal("1200.00").compareTo(extrato.getSaldoAnterior()));
        List<LinhaExtratoResponse> linhas = extrato.getLinhas();
        Assertions.assertEquals(3, linhas.size());
        Assertions.assertEquals(1L, linhas.get(0).getLancamentoId());
        Assertions.assertEquals(0, new BigDecimal("4200.00").compareTo(linhas.get(0).getSaldo()));
        // No mesmo dia, pela ordem de registro: o pagamento (8h) antes da despesa (12h).
        Assertions.assertEquals(9L, linhas.get(1).getMovimentacaoId());
        Assertions.assertEquals(TipoLancamento.D, linhas.get(1).getTipo());
        Assertions.assertEquals("Pagamento da fatura Personnalité", linhas.get(1).getDescricao());
        Assertions.assertEquals(0, new BigDecimal("3000.00").compareTo(linhas.get(1).getSaldo()));
        Assertions.assertEquals(0, new BigDecimal("2950.00").compareTo(linhas.get(2).getSaldo()));
        Assertions.assertEquals(0, new BigDecimal("3000.00").compareTo(extrato.getEntradas()));
        Assertions.assertEquals(0, new BigDecimal("1250.00").compareTo(extrato.getSaidas()));
        Assertions.assertEquals(0, new BigDecimal("2950.00").compareTo(extrato.getSaldoFinal()));

    }

    @Test
    @DisplayName("Sem datas, o extrato é o do ciclo atual")
    public void semDatasUsaCicloAtual() {

        Mockito.when(configuracaoService.diaInicioCiclo(USUARIO)).thenReturn(5);

        ExtratoContaResponse extrato = service.extrato(2L, null, null);

        Assertions.assertEquals(LocalDate.of(2026, 11, 5), extrato.getInicio());
        Assertions.assertEquals(LocalDate.of(2026, 12, 4), extrato.getFim());
        Assertions.assertEquals(0, new BigDecimal("1000.00").compareTo(extrato.getSaldoFinal()));

    }

    @Test
    @DisplayName("Data inicial depois da final é recusada")
    public void inicioDepoisDoFim() {

        Assertions.assertThrows(ExceptionDefault.class, () -> service.extrato(2L, "10/11/2026", "01/11/2026"));

    }

}
