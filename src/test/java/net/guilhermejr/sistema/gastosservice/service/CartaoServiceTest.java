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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.YearMonth;

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

}
