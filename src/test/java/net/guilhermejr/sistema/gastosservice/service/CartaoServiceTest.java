package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.CartaoMapper;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.CartaoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.MovimentacaoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.RecorrenciaRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.util.CalendarioUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class CartaoServiceTest {

    /** Vence dia 10 e fecha dia 3: a compra de 07/10 cai na fatura de novembro. */
    private static final LocalDate COMPRA = LocalDate.of(2026, 10, 7);
    private static final YearMonth NATURAL = YearMonth.of(2026, 11);

    private CartaoService cartaoService;
    private MovimentacaoRepository movimentacaoRepository;
    private Lancamento lancamento;

    @BeforeEach
    public void preparar() {

        movimentacaoRepository = Mockito.mock(MovimentacaoRepository.class);
        cartaoService = new CartaoService(Mockito.mock(CartaoRepository.class), Mockito.mock(LancamentoRepository.class),
                movimentacaoRepository, Mockito.mock(RecorrenciaRepository.class), Mockito.mock(ContaService.class), Mockito.mock(CartaoMapper.class),
                Mockito.mock(AuthenticationCurrentUserService.class));

        Cartao cartao = new Cartao();
        cartao.setDiaVencimento(10);
        cartao.setDiasFechamento(7);
        lancamento = new Lancamento();
        lancamento.setCartao(cartao);
        lancamento.setData(COMPRA);
        cartaoService.posicionarNaFatura(lancamento, false);

    }

    @Test
    @DisplayName("Transfere para a próxima fatura e marca como transferida")
    public void transfere_para_a_proxima() {

        cartaoService.transferirParaFatura(lancamento, NATURAL.plusMonths(1));

        Assertions.assertEquals(LocalDate.of(2026, 12, 10), lancamento.getFatura());
        Assertions.assertTrue(lancamento.getFaturaTransferida());

    }

    @Test
    @DisplayName("Aceita a fatura imediatamente anterior à natural")
    public void aceita_a_fatura_anterior() {

        cartaoService.transferirParaFatura(lancamento, NATURAL.minusMonths(1));

        Assertions.assertEquals(LocalDate.of(2026, 10, 10), lancamento.getFatura());
        Assertions.assertTrue(lancamento.getFaturaTransferida());

    }

    @Test
    @DisplayName("Recusa duas faturas antes da natural")
    public void recusa_duas_faturas_antes() {

        Assertions.assertThrows(ExceptionDefault.class, () -> cartaoService.transferirParaFatura(lancamento, NATURAL.minusMonths(2)));
        Assertions.assertEquals(LocalDate.of(2026, 11, 10), lancamento.getFatura());

    }

    @Test
    @DisplayName("Voltar para a natural tira a marca de transferida")
    public void voltar_para_a_natural_desmarca() {

        cartaoService.transferirParaFatura(lancamento, NATURAL.minusMonths(1));
        cartaoService.transferirParaFatura(lancamento, NATURAL);

        Assertions.assertEquals(LocalDate.of(2026, 11, 10), lancamento.getFatura());
        Assertions.assertFalse(lancamento.getFaturaTransferida());

    }

    @Test
    @DisplayName("Reposicionar mantém a compra na fatura anterior escolhida")
    public void reposicionar_mantem_a_anterior() {

        cartaoService.transferirParaFatura(lancamento, NATURAL.minusMonths(1));
        cartaoService.posicionarNaFatura(lancamento, true);

        Assertions.assertEquals(LocalDate.of(2026, 10, 10), lancamento.getFatura());
        Assertions.assertTrue(lancamento.getFaturaTransferida());

    }

    @Test
    @DisplayName("Reposicionar devolve à natural a escolha que a nova data deixou para trás")
    public void reposicionar_devolve_escolha_que_ficou_para_tras() {

        cartaoService.transferirParaFatura(lancamento, NATURAL.minusMonths(1));
        lancamento.setData(LocalDate.of(2026, 11, 7));
        cartaoService.posicionarNaFatura(lancamento, true);

        Assertions.assertEquals(LocalDate.of(2026, 12, 10), lancamento.getFatura());
        Assertions.assertFalse(lancamento.getFaturaTransferida());

    }

    private void faturaPaga(YearMonth mes) {
        Mockito.when(movimentacaoRepository.existsByCartaoAndFaturaBetween(Mockito.any(), Mockito.eq(mes.atDay(1)), Mockito.eq(mes.atEndOfMonth())))
                .thenReturn(true);
    }

    @Test
    @DisplayName("Recusa levar para a fatura anterior já paga")
    public void recusa_fatura_anterior_paga() {

        faturaPaga(NATURAL.minusMonths(1));

        ExceptionDefault erro = Assertions.assertThrows(ExceptionDefault.class,
                () -> cartaoService.transferirParaFatura(lancamento, NATURAL.minusMonths(1)));
        Assertions.assertTrue(erro.getMessage().contains("já foi paga"));
        Assertions.assertEquals(LocalDate.of(2026, 11, 10), lancamento.getFatura());

    }

    @Test
    @DisplayName("Recusa voltar uma compra transferida para a fatura já paga")
    public void recusa_voltar_para_fatura_paga() {

        cartaoService.transferirParaFatura(lancamento, NATURAL.plusMonths(1));
        faturaPaga(NATURAL);

        Assertions.assertThrows(ExceptionDefault.class, () -> cartaoService.transferirParaFatura(lancamento, NATURAL));
        Assertions.assertEquals(LocalDate.of(2026, 12, 10), lancamento.getFatura());

    }

    @Test
    @DisplayName("Levar para a frente não olha se a fatura foi paga")
    public void levar_para_frente_ignora_pagamento() {

        faturaPaga(NATURAL.plusMonths(1));
        cartaoService.transferirParaFatura(lancamento, NATURAL.plusMonths(1));

        Assertions.assertEquals(LocalDate.of(2026, 12, 10), lancamento.getFatura());

    }

    private static Lancamento pendente(Cartao cartao, LocalDate data) {
        Lancamento l = new Lancamento();
        l.setCartao(cartao);
        l.setData(data);
        l.setRealizado(false);
        l.setFaturaTransferida(false);
        l.setFatura(CalendarioUtil.vencimentoDaCompra(data, cartao.getDiaVencimento(), cartao.getDiasFechamento()));
        return l;
    }

    @Test
    @DisplayName("Vencimento mudado pelo banco: a fatura fechada fica, da aberta em diante segue o novo ciclo")
    public void mudar_vencimento_pelo_banco() {

        LancamentoRepository lancamentoRepository = Mockito.mock(LancamentoRepository.class);
        CartaoService servico = new CartaoService(Mockito.mock(CartaoRepository.class), lancamentoRepository,
                Mockito.mock(MovimentacaoRepository.class), Mockito.mock(RecorrenciaRepository.class), Mockito.mock(ContaService.class),
                Mockito.mock(CartaoMapper.class), Mockito.mock(AuthenticationCurrentUserService.class));

        // Vencia dia 6 (fechando dia 29/30) e passa a vencer dia 25 (fechando dia 18).
        Cartao cartao = new Cartao();
        cartao.setDiaVencimento(6);
        cartao.setDiasFechamento(7);
        cartao.setBancoInicioFatura(LocalDate.of(2026, 11, 6));
        Lancamento fechada = pendente(cartao, LocalDate.of(2026, 11, 20));   // 06/12, fatura que já fechou
        Lancamento antesDoFechamento = pendente(cartao, LocalDate.of(2026, 12, 5));  // 06/01
        Lancamento noFechamento = pendente(cartao, LocalDate.of(2026, 12, 18));      // 06/01
        Lancamento parcela = pendente(cartao, LocalDate.of(2027, 3, 26));            // 06/04
        Mockito.when(lancamentoRepository.findAllByCartaoAndRealizadoFalse(cartao))
                .thenReturn(List.of(fechada, antesDoFechamento, noFechamento, parcela));

        // Em 19/12, com o novo ciclo, a fatura aberta é a de janeiro.
        servico.mudarVencimento(cartao, 25, YearMonth.of(2027, 1));

        Assertions.assertEquals(25, cartao.getDiaVencimento());
        Assertions.assertEquals(LocalDate.of(2026, 11, 25), cartao.getBancoInicioFatura());
        Assertions.assertEquals(LocalDate.of(2026, 12, 25), fechada.getFatura());
        // Comprada antes do novo fechamento (18/12): o banco a pôs na fatura de dezembro.
        Assertions.assertEquals(LocalDate.of(2026, 12, 25), antesDoFechamento.getFatura());
        Assertions.assertEquals(LocalDate.of(2027, 1, 25), noFechamento.getFatura());
        Assertions.assertEquals(LocalDate.of(2027, 4, 25), parcela.getFatura());

    }
}
