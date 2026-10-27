package net.guilhermejr.sistema.gastosservice.util;

import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.Movimentacao;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.SugestaoConciliacao;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoMovimentacao;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Casos tirados do extrato da conta corrente do Itaú (out/2026). */
public class ConciliadorContaTest {

    private static long ids = 1;

    private static final Conta ITAU = conta(100L);

    private static Conta conta(Long id) {
        Conta conta = new Conta();
        conta.setId(id);
        return conta;
    }

    /** Valor negativo = saída da conta, como a Pluggy manda. */
    private static TransacaoBanco banco(String valor, String data, String descricao) {
        TransacaoBanco t = new TransacaoBanco();
        t.setId(ids++);
        t.setTipo(valor.startsWith("-") ? TipoLancamento.D : TipoLancamento.R);
        t.setValor(new BigDecimal(valor.replace("-", "")));
        t.setData(LocalDate.parse(data));
        t.setDescricao(descricao);
        return t;
    }

    private static ConciliadorConta.Item lancamento(TipoLancamento tipo, String valor, String data) {
        Lancamento l = new Lancamento();
        l.setId(ids++);
        l.setTipo(tipo);
        l.setValor(new BigDecimal(valor));
        l.setData(LocalDate.parse(data));
        return ConciliadorConta.Item.de(l);
    }

    private static ConciliadorConta.Item pagamentoFatura(String valor, String data) {
        Movimentacao m = new Movimentacao();
        m.setId(ids++);
        m.setTipo(TipoMovimentacao.PAGAMENTO_FATURA);
        m.setValor(new BigDecimal(valor));
        m.setData(LocalDate.parse(data));
        m.setContaOrigem(ITAU);
        m.setCartao(new Cartao());
        return ConciliadorConta.Item.de(m, ITAU);
    }

    private static ConciliadorConta.Sugestao unica(ConciliadorConta.Resultado resultado) {
        Assertions.assertEquals(1, resultado.sugestoes().size());
        return resultado.sugestoes().get(0);
    }

    @Test
    @DisplayName("Aplicação e resgate da aplicação automática são ignorados")
    public void aplicacao_automatica() {

        ConciliadorConta.Resultado resultado = ConciliadorConta.sugerir(List.of(
                banco("100", "2026-10-03", "Resgate RES APLIC AUT MAIS"),
                banco("-9225.40", "2026-10-01", "Aplicação APL APLIC AUT MAIS")), List.of());

        Assertions.assertTrue(resultado.sugestoes().stream().allMatch(s -> s.tipo() == SugestaoConciliacao.IGNORAR));

    }

    @Test
    @DisplayName("Rendimento pago no resgate já estava no saldo do banco: é ignorado")
    public void rendimento_ignorado() {

        // Caso real de 15/10/2026: o Pix de 400,00 resgatou 399,97 + 0,03 de rendimento e o
        // saldo do banco não mudou; importar os 0,03 deixou o sistema 0,03 acima
        ConciliadorConta.Resultado resultado = ConciliadorConta.sugerir(List.of(
                banco("399.97", "2026-10-15", "Entrada RES APLIC AUT MAIS AP"),
                banco("0.03", "2026-10-15", "Entrada REND PAGO APLIC AUT APR")), List.of());

        Assertions.assertTrue(resultado.sugestoes().stream().allMatch(s -> s.tipo() == SugestaoConciliacao.IGNORAR));

    }

    @Test
    @DisplayName("Fixo pago dois dias antes, com o mesmo valor, casa")
    public void fixo_pago_antes() {

        ConciliadorConta.Item carro = lancamento(TipoLancamento.D, "2395.26", "2026-10-30");
        ConciliadorConta.Sugestao sugestao = unica(ConciliadorConta.sugerir(
                List.of(banco("-2395.26", "2026-10-28", "Pagamento de financiamento PAGTO FINANC VEIC 07/48")), List.of(carro)));

        Assertions.assertEquals(SugestaoConciliacao.CONCILIAR, sugestao.tipo());
        Assertions.assertEquals(List.of(carro), sugestao.candidatos());

    }

    @Test
    @DisplayName("Mesmo valor longe demais na data não casa")
    public void mesmo_valor_longe() {

        ConciliadorConta.Resultado resultado = ConciliadorConta.sugerir(
                List.of(banco("-100", "2026-10-01", "Pix enviado FRANCISCO")), List.of(lancamento(TipoLancamento.D, "100.00", "2026-11-01")));

        Assertions.assertEquals(SugestaoConciliacao.CRIAR, unica(resultado).tipo());
        Assertions.assertEquals(1, resultado.semPar().size());

    }

    @Test
    @DisplayName("Mesmo valor casa com até 15 dias de diferença, não com 16")
    public void mesmo_valor_ate_15_dias() {

        ConciliadorConta.Item pix = lancamento(TipoLancamento.D, "100.00", "2026-10-16");
        Assertions.assertEquals(SugestaoConciliacao.CONCILIAR, unica(ConciliadorConta.sugerir(
                List.of(banco("-100", "2026-10-01", "Pix enviado FRANCISCO")), List.of(pix))).tipo());
        Assertions.assertEquals(SugestaoConciliacao.CRIAR, unica(ConciliadorConta.sugerir(
                List.of(banco("-100", "2026-09-30", "Pix enviado FRANCISCO")), List.of(pix))).tipo());

    }

    @Test
    @DisplayName("Entrada não casa com saída do mesmo valor")
    public void sentido_diferente() {

        ConciliadorConta.Sugestao sugestao = unica(ConciliadorConta.sugerir(
                List.of(banco("100", "2026-10-01", "Pix recebido")), List.of(lancamento(TipoLancamento.D, "100.00", "2026-10-01"))));

        Assertions.assertEquals(SugestaoConciliacao.CRIAR, sugestao.tipo());

    }

    @Test
    @DisplayName("Pagamento da fatura no banco casa com a movimentação de pagamento")
    public void pagamento_de_fatura() {

        ConciliadorConta.Item pagamento = pagamentoFatura("3810.12", "2026-10-10");
        ConciliadorConta.Sugestao sugestao = unica(ConciliadorConta.sugerir(
                List.of(banco("-3810.12", "2026-10-10", "Pagamento de fatura Fatura Paga")), List.of(pagamento)));

        Assertions.assertEquals(SugestaoConciliacao.CONCILIAR, sugestao.tipo());
        Assertions.assertSame(pagamento.movimentacao(), sugestao.candidatos().get(0).movimentacao());
        Assertions.assertEquals(TipoLancamento.D, pagamento.tipo());

    }

    @Test
    @DisplayName("Movimentação que entra na conta é do tipo R")
    public void movimentacao_que_entra() {

        Movimentacao m = new Movimentacao();
        m.setTipo(TipoMovimentacao.TRANSFERENCIA);
        m.setValor(BigDecimal.TEN);
        m.setData(LocalDate.parse("2026-10-01"));
        m.setContaOrigem(conta(200L));
        m.setContaDestino(ITAU);

        Assertions.assertEquals(TipoLancamento.R, ConciliadorConta.Item.de(m, ITAU).tipo());

    }

    @Test
    @DisplayName("Valor um pouco diferente sugere corrigir; movimentação não entra nisso")
    public void valor_diferente() {

        ConciliadorConta.Item seguro = lancamento(TipoLancamento.D, "178.64", "2026-10-27");
        ConciliadorConta.Sugestao sugestao = unica(ConciliadorConta.sugerir(
                List.of(banco("-180.10", "2026-10-27", "Débito automático ITAU SEGUROS")),
                List.of(seguro, pagamentoFatura("180.00", "2026-10-27"))));

        Assertions.assertEquals(SugestaoConciliacao.VALOR_DIFERENTE, sugestao.tipo());
        Assertions.assertEquals(List.of(seguro), sugestao.candidatos());

    }

    @Test
    @DisplayName("Dois lançamentos de mesmo valor para uma transação: o usuário escolhe")
    public void escolher() {

        ConciliadorConta.Item fiap = lancamento(TipoLancamento.D, "510.00", "2026-11-01");
        ConciliadorConta.Item outro = lancamento(TipoLancamento.D, "510.00", "2026-11-03");
        ConciliadorConta.Sugestao sugestao = unica(ConciliadorConta.sugerir(
                List.of(banco("-510", "2026-11-02", "Pagamento de Pix QR Code VSTP")), List.of(fiap, outro)));

        Assertions.assertEquals(SugestaoConciliacao.ESCOLHER, sugestao.tipo());
        Assertions.assertEquals(2, sugestao.candidatos().size());

    }

    @Test
    @DisplayName("Duas transações e dois lançamentos de mesmo valor casam em ordem de data")
    public void pares_em_ordem() {

        ConciliadorConta.Item primeiro = lancamento(TipoLancamento.D, "100.00", "2026-11-01");
        ConciliadorConta.Item segundo = lancamento(TipoLancamento.D, "100.00", "2026-11-15");
        TransacaoBanco b1 = banco("-100", "2026-11-02", "Pix enviado");
        TransacaoBanco b2 = banco("-100", "2026-11-16", "Pix enviado");
        ConciliadorConta.Resultado resultado = ConciliadorConta.sugerir(List.of(b2, b1), List.of(segundo, primeiro));

        Assertions.assertTrue(resultado.sugestoes().stream().allMatch(s -> s.tipo() == SugestaoConciliacao.CONCILIAR));
        Assertions.assertEquals(List.of(primeiro), resultado.sugestoes().stream().filter(s -> s.transacao() == b1).findFirst().orElseThrow().candidatos());
        Assertions.assertTrue(resultado.semPar().isEmpty());

    }

}
