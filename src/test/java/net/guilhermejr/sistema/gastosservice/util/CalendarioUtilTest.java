package net.guilhermejr.sistema.gastosservice.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class CalendarioUtilTest {

    // Cartão que vence dia 10 e fecha 7 dias antes: fechamento no dia 3.

    @Test
    @DisplayName("Compra antes do fechamento entra na fatura do mês")
    public void compra_antes_do_fechamento_entra_na_fatura_do_mes() {

        Assertions.assertEquals(LocalDate.of(2026, 10, 10),
                CalendarioUtil.vencimentoDaCompra(LocalDate.of(2026, 10, 2), 10, 7));

    }

    @Test
    @DisplayName("Compra no dia do fechamento já vai para a fatura seguinte")
    public void compra_no_dia_do_fechamento_vai_para_a_fatura_seguinte() {

        Assertions.assertEquals(LocalDate.of(2026, 11, 10),
                CalendarioUtil.vencimentoDaCompra(LocalDate.of(2026, 10, 3), 10, 7));

    }

    @Test
    @DisplayName("Compra entre o vencimento e o fim do mês vai para o mês seguinte")
    public void compra_depois_do_vencimento_vai_para_o_mes_seguinte() {

        Assertions.assertEquals(LocalDate.of(2026, 11, 10),
                CalendarioUtil.vencimentoDaCompra(LocalDate.of(2026, 10, 25), 10, 7));

    }

    @Test
    @DisplayName("Fechamento no mês anterior ao vencimento empurra a compra dois meses")
    public void fechamento_no_mes_anterior_empurra_a_compra_dois_meses() {

        // Vence dia 5 e fecha 10 dias antes: a fatura de novembro fecha em 26/10.
        Assertions.assertEquals(LocalDate.of(2026, 11, 5),
                CalendarioUtil.vencimentoDaCompra(LocalDate.of(2026, 10, 20), 5, 10));
        Assertions.assertEquals(LocalDate.of(2026, 12, 5),
                CalendarioUtil.vencimentoDaCompra(LocalDate.of(2026, 10, 26), 5, 10));

    }

    @Test
    @DisplayName("Vencimento no dia 31 recua para o último dia em meses curtos")
    public void vencimento_dia_31_recua_em_meses_curtos() {

        Assertions.assertEquals(LocalDate.of(2027, 2, 28), CalendarioUtil.vencimentoNoMes(YearMonth.of(2027, 2), 31));
        Assertions.assertEquals(LocalDate.of(2028, 2, 29), CalendarioUtil.vencimentoNoMes(YearMonth.of(2028, 2), 31));
        // Fevereiro vence 28/02 e fecha 21/02: comprar em 21/02 já cai em março.
        Assertions.assertEquals(LocalDate.of(2027, 3, 31),
                CalendarioUtil.vencimentoDaCompra(LocalDate.of(2027, 2, 21), 31, 7));

    }

    @Test
    @DisplayName("Repetição mensal volta ao dia original depois de um mês curto")
    public void repeticao_mensal_volta_ao_dia_original() {

        LocalDate inicio = LocalDate.of(2027, 1, 31);
        Assertions.assertEquals(LocalDate.of(2027, 2, 28), CalendarioUtil.mesesDepois(inicio, 31, 1));
        Assertions.assertEquals(LocalDate.of(2027, 3, 31), CalendarioUtil.mesesDepois(inicio, 31, 2));
        // Mesmo partindo de uma data já recuada.
        Assertions.assertEquals(LocalDate.of(2027, 3, 31), CalendarioUtil.mesesDepois(LocalDate.of(2027, 2, 28), 31, 1));
        Assertions.assertEquals(LocalDate.of(2027, 1, 15), CalendarioUtil.mesesDepois(LocalDate.of(2026, 12, 15), 15, 1));

    }

    @Test
    @DisplayName("Parcelas somam exatamente o total, com o resto na primeira")
    public void parcelas_somam_o_total() {

        List<BigDecimal> parcelas = CalendarioUtil.dividirEmParcelas(new BigDecimal("100.00"), 3);

        Assertions.assertEquals(List.of(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33")), parcelas);
        Assertions.assertEquals(new BigDecimal("100.00"), parcelas.stream().reduce(BigDecimal.ZERO, BigDecimal::add));

    }

    @Test
    @DisplayName("Parcelas de divisão exata saem iguais")
    public void parcelas_de_divisao_exata_saem_iguais() {

        Assertions.assertEquals(List.of(new BigDecimal("50.00"), new BigDecimal("50.00")),
                CalendarioUtil.dividirEmParcelas(new BigDecimal("100.00"), 2));

    }

    @Test
    @DisplayName("Valor menor que o número de parcelas não gera parcela negativa")
    public void valor_pequeno_nao_gera_parcela_negativa() {

        List<BigDecimal> parcelas = CalendarioUtil.dividirEmParcelas(new BigDecimal("0.10"), 12);

        Assertions.assertTrue(parcelas.stream().allMatch(p -> p.signum() >= 0));
        Assertions.assertEquals(new BigDecimal("0.10"), parcelas.stream().reduce(BigDecimal.ZERO, BigDecimal::add));

    }

}
