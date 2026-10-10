package net.guilhermejr.sistema.gastosservice.util;

import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.SugestaoConciliacao;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Casos tirados da fatura de novembro/2026 do Personnalité. */
public class ConciliadorTest {

    private static long ids = 1;

    private static TransacaoBanco banco(String valor, String data, String descricao, Integer parcela, Integer total) {
        TransacaoBanco t = new TransacaoBanco();
        t.setId(ids++);
        t.setTipo(valor.startsWith("-") ? TipoLancamento.R : TipoLancamento.D);
        t.setValor(new BigDecimal(valor.replace("-", "")));
        t.setData(LocalDate.parse(data));
        t.setDescricao(descricao);
        t.setParcela(parcela);
        t.setTotalParcelas(total);
        return t;
    }

    private static TransacaoBanco banco(String valor, String data) {
        return banco(valor, data, "COMPRA", null, null);
    }

    private static Lancamento sistema(String valor, String data, Integer parcela, Integer total) {
        Lancamento l = new Lancamento();
        l.setId(ids++);
        l.setTipo(TipoLancamento.D);
        l.setValor(new BigDecimal(valor));
        l.setData(LocalDate.parse(data));
        l.setParcela(parcela);
        l.setTotalParcelas(total);
        return l;
    }

    private static Lancamento sistema(String valor, String data) {
        return sistema(valor, data, null, null);
    }

    private static Conciliador.Sugestao unica(Conciliador.Resultado resultado) {
        Assertions.assertEquals(1, resultado.sugestoes().size());
        return resultado.sugestoes().get(0);
    }

    @Test
    @DisplayName("Mesmo valor à vista casa, mesmo com a data do sistema diferente")
    public void casa_valor_exato() {

        Lancamento jantar = sistema("148.85", "2026-10-01");
        Conciliador.Sugestao s = unica(Conciliador.sugerir(List.of(banco("148.85", "2026-09-30")), List.of(jantar)));

        Assertions.assertEquals(SugestaoConciliacao.CONCILIAR, s.tipo());
        Assertions.assertEquals(List.of(jantar), s.candidatos());

    }

    @Test
    @DisplayName("Parcela casa pelas restantes: 5/11 no banco é 1/7 no sistema")
    public void parcela_pelas_restantes() {

        Lancamento iphone = sistema("999.90", "2026-10-20", 1, 7);
        Conciliador.Sugestao s = unica(Conciliador.sugerir(
                List.of(banco("999.90", "2026-10-27", "AMAZON BRSAO PAULO05/11", 5, 11)), List.of(iphone)));

        Assertions.assertEquals(SugestaoConciliacao.CONCILIAR, s.tipo());
        Assertions.assertEquals(List.of(iphone), s.candidatos());

    }

    @Test
    @DisplayName("Parcela não casa com compra à vista de mesmo valor, nem com outra quantidade de restantes")
    public void parcela_nao_casa_com_a_vista() {

        Conciliador.Resultado r = Conciliador.sugerir(List.of(banco("999.90", "2026-10-27", "AMAZON05/11", 5, 11)),
                List.of(sistema("999.90", "2026-10-20"), sistema("999.90", "2026-10-20", 1, 5)));

        Assertions.assertEquals(SugestaoConciliacao.CRIAR, unica(r).tipo());
        Assertions.assertEquals(2, r.semPar().size());

    }

    @Test
    @DisplayName("A última parcela casa com lançamento à vista: 10/10 no banco é uma compra sem parcelas")
    public void ultima_parcela_casa_com_a_vista() {

        Lancamento ferreira = sistema("62.52", "2026-10-03");
        Conciliador.Sugestao s = unica(Conciliador.sugerir(
                List.of(banco("62.52", "2026-10-27", "FERREIRA COSTASALV10/10", 10, 10)), List.of(ferreira)));

        Assertions.assertEquals(SugestaoConciliacao.CONCILIAR, s.tipo());
        Assertions.assertEquals(List.of(ferreira), s.candidatos());

    }

    @Test
    @DisplayName("Duas compras iguais no banco e duas no sistema casam em ordem de data")
    public void iguais_em_ordem_de_data() {

        Lancamento primeiro = sistema("61.80", "2026-10-01");
        Lancamento segundo = sistema("61.80", "2026-10-09");
        TransacaoBanco b1 = banco("61.80", "2026-09-28");
        TransacaoBanco b2 = banco("61.80", "2026-10-09");
        Conciliador.Resultado r = Conciliador.sugerir(List.of(b2, b1), List.of(segundo, primeiro));

        Assertions.assertTrue(r.sugestoes().stream().allMatch(s -> s.tipo() == SugestaoConciliacao.CONCILIAR));
        Assertions.assertEquals(List.of(primeiro), r.sugestoes().stream().filter(s -> s.transacao() == b1).findFirst().orElseThrow().candidatos());
        Assertions.assertEquals(List.of(segundo), r.sugestoes().stream().filter(s -> s.transacao() == b2).findFirst().orElseThrow().candidatos());

    }

    @Test
    @DisplayName("Um no banco e dois no sistema com o mesmo valor: o usuário escolhe, o mais próximo primeiro")
    public void escolher_entre_candidatos() {

        Lancamento appleOne = sistema("64.90", "2026-10-03");
        Lancamento airpods = sistema("64.90", "2026-10-20");
        Conciliador.Resultado r = Conciliador.sugerir(List.of(banco("64.90", "2026-10-03")), List.of(airpods, appleOne));

        Assertions.assertEquals(SugestaoConciliacao.ESCOLHER, unica(r).tipo());
        Assertions.assertEquals(List.of(appleOne, airpods), unica(r).candidatos());
        Assertions.assertTrue(r.semPar().isEmpty());

    }

    @Test
    @DisplayName("Valor um pouco diferente e data próxima: sugere corrigir (Disney 69,90 × 66,90)")
    public void valor_diferente() {

        Lancamento disney = sistema("66.90", "2026-10-01");
        Conciliador.Sugestao s = unica(Conciliador.sugerir(List.of(banco("69.90", "2026-09-29")), List.of(disney)));

        Assertions.assertEquals(SugestaoConciliacao.VALOR_DIFERENTE, s.tipo());
        Assertions.assertEquals(List.of(disney), s.candidatos());

    }

    @Test
    @DisplayName("IOF separado: a compra casa com o lançamento de valor próximo e o IOF vira lançamento novo")
    public void iof_separado() {

        Lancamento claude = sistema("120.87", "2026-10-01");
        TransacaoBanco compra = banco("115.94", "2026-09-30");
        TransacaoBanco iof = banco("4.06", "2026-09-30");
        Conciliador.Resultado r = Conciliador.sugerir(List.of(compra, iof), List.of(claude));

        Assertions.assertEquals(SugestaoConciliacao.VALOR_DIFERENTE, r.sugestoes().stream().filter(s -> s.transacao() == compra).findFirst().orElseThrow().tipo());
        Assertions.assertEquals(SugestaoConciliacao.CRIAR, r.sugestoes().stream().filter(s -> s.transacao() == iof).findFirst().orElseThrow().tipo());

    }

    @Test
    @DisplayName("Valor próximo mas longe na data, ou longe no valor: criar")
    public void longe_vira_criar() {

        Conciliador.Resultado r = Conciliador.sugerir(List.of(banco("68.40", "2026-10-09")),
                List.of(sistema("61.80", "2026-10-09"), sistema("68.00", "2026-10-20")));

        Assertions.assertEquals(SugestaoConciliacao.CRIAR, unica(r).tipo());
        Assertions.assertEquals(2, r.semPar().size());

    }

    @Test
    @DisplayName("O pagamento da fatura anterior é ignorado; estorno não")
    public void pagamento_ignorado() {

        Conciliador.Resultado r = Conciliador.sugerir(List.of(banco("-6983.30", "2026-09-29", "PAGAMENTO COM SALDO", null, null),
                banco("-30.67", "2026-10-02", "CANCELAMENTO DE COMPRA - AMAZON", null, null)), List.of());

        Assertions.assertEquals(SugestaoConciliacao.IGNORAR, r.sugestoes().stream().filter(s -> s.transacao().getValor().compareTo(new BigDecimal("6983.30")) == 0).findFirst().orElseThrow().tipo());
        Assertions.assertEquals(SugestaoConciliacao.CRIAR, r.sugestoes().stream().filter(s -> s.transacao().getValor().compareTo(new BigDecimal("30.67")) == 0).findFirst().orElseThrow().tipo());

    }

    @Test
    @DisplayName("Lançamento do sistema sem nada no banco fica sem par")
    public void sem_par() {

        Lancamento starlink = sistema("249.00", "2026-10-26");
        Conciliador.Resultado r = Conciliador.sugerir(List.of(), List.of(starlink));

        Assertions.assertEquals(List.of(starlink), r.semPar());

    }

}
