package net.guilhermejr.sistema.gastosservice.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

public class CicloTest {

    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    @Test
    @DisplayName("Dia 1 é o mês do calendário")
    public void dia_1_e_o_mes_do_calendario() {

        Ciclo ciclo = Ciclo.chamado(OUTUBRO, 1);

        Assertions.assertEquals(LocalDate.of(2026, 10, 1), ciclo.inicio());
        Assertions.assertEquals(LocalDate.of(2026, 10, 31), ciclo.fim());
        Assertions.assertEquals(ciclo, Ciclo.daData(LocalDate.of(2026, 10, 31), 1));

    }

    @Test
    @DisplayName("Até o dia 15 o ciclo leva o nome do mês em que começa")
    public void ate_o_dia_15_leva_o_mes_em_que_comeca() {

        Ciclo ciclo = Ciclo.chamado(OUTUBRO, 5);

        Assertions.assertEquals(LocalDate.of(2026, 10, 5), ciclo.inicio());
        Assertions.assertEquals(LocalDate.of(2026, 11, 4), ciclo.fim());

    }

    @Test
    @DisplayName("Do dia 16 em diante o ciclo leva o nome do mês seguinte")
    public void do_dia_16_em_diante_leva_o_mes_seguinte() {

        Ciclo ciclo = Ciclo.chamado(OUTUBRO, 25);

        Assertions.assertEquals(LocalDate.of(2026, 9, 25), ciclo.inicio());
        Assertions.assertEquals(LocalDate.of(2026, 10, 24), ciclo.fim());

    }

    @Test
    @DisplayName("A data cai no ciclo que começou no último dia de início")
    public void data_cai_no_ciclo_certo() {

        Assertions.assertEquals(YearMonth.of(2026, 9), Ciclo.daData(LocalDate.of(2026, 10, 4), 5).nome());
        Assertions.assertEquals(OUTUBRO, Ciclo.daData(LocalDate.of(2026, 10, 5), 5).nome());
        Assertions.assertEquals(OUTUBRO, Ciclo.daData(LocalDate.of(2026, 10, 24), 25).nome());
        Assertions.assertEquals(YearMonth.of(2026, 11), Ciclo.daData(LocalDate.of(2026, 10, 25), 25).nome());

    }

    @Test
    @DisplayName("Todo mês do ano tem um ciclo, sem buraco nem sobreposição, para qualquer dia")
    public void ciclos_seguidos_cobrem_o_ano() {

        for (int dia = 1; dia <= 28; dia++) {
            Ciclo anterior = Ciclo.chamado(YearMonth.of(2027, 1), dia);
            for (int m = 2; m <= 12; m++) {
                Ciclo ciclo = Ciclo.chamado(YearMonth.of(2027, m), dia);
                Assertions.assertEquals(anterior.fim().plusDays(1), ciclo.inicio(), "dia " + dia + ", mês " + m);
                Assertions.assertEquals(ciclo, Ciclo.daData(ciclo.inicio(), dia));
                Assertions.assertEquals(ciclo, Ciclo.daData(ciclo.fim(), dia));
                anterior = ciclo;
            }
        }

    }

    @Test
    @DisplayName("Cada cartão tem a fatura cujo vencimento cai dentro do ciclo")
    public void fatura_do_cartao_no_ciclo() {

        Ciclo ciclo = Ciclo.chamado(OUTUBRO, 5); // 05/10 a 04/11

        Assertions.assertEquals(OUTUBRO, ciclo.mesDaFatura(10));
        Assertions.assertEquals(OUTUBRO, ciclo.mesDaFatura(5));
        Assertions.assertEquals(OUTUBRO, ciclo.mesDaFatura(31));
        Assertions.assertEquals(YearMonth.of(2026, 11), ciclo.mesDaFatura(4));
        Assertions.assertEquals(OUTUBRO, Ciclo.chamado(OUTUBRO, 1).mesDaFatura(1));

        for (int dia = 1; dia <= 28; dia++) {
            for (int vencimento = 1; vencimento <= 31; vencimento++) {
                Ciclo c = Ciclo.chamado(YearMonth.of(2027, 2), dia);
                LocalDate data = CalendarioUtil.vencimentoNoMes(c.mesDaFatura(vencimento), vencimento);
                Assertions.assertFalse(data.isBefore(c.inicio()) || data.isAfter(c.fim()), "dia " + dia + ", vencimento " + vencimento);
            }
        }

    }

}
