package net.guilhermejr.sistema.gastosservice.util;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * O "mês" do dashboard e do relatório: de {@code inicio} a {@code fim}, inclusive,
 * chamado pelo mês {@code nome}. Com o dia de início 1 é o mês do calendário.
 *
 * <p>Com outro dia, o ciclo atravessa dois meses (dia 5: 05/10 a 04/11) e leva o nome
 * do que tem mais dias dele: até o dia 15, o mês em que começa; do 16 em diante, o
 * seguinte (dia 25: 25/09 a 24/10 é outubro). O corte é fixo pelo dia, não pela conta
 * dos dias de cada mês, para que todo mês tenha exatamente um ciclo — contando, o
 * fevereiro curto faria um ciclo pular o nome de um mês.
 *
 * <p>O dia vai só até 28, então o ciclo começa no mesmo dia em todos os meses.
 */
public record Ciclo(YearMonth nome, LocalDate inicio, LocalDate fim) {

    /** A partir deste dia de início, o ciclo leva o nome do mês seguinte ao em que começa. */
    static final int DIA_NOME_MES_SEGUINTE = 16;

    public static Ciclo chamado(YearMonth nome, int diaInicio) {

        YearMonth comeca = diaInicio >= DIA_NOME_MES_SEGUINTE ? nome.minusMonths(1) : nome;
        LocalDate inicio = comeca.atDay(diaInicio);
        return new Ciclo(nome, inicio, inicio.plusMonths(1).minusDays(1));

    }

    /** O ciclo em que cai a data. */
    public static Ciclo daData(LocalDate data, int diaInicio) {

        YearMonth comeca = data.getDayOfMonth() >= diaInicio ? YearMonth.from(data) : YearMonth.from(data).minusMonths(1);
        return chamado(diaInicio >= DIA_NOME_MES_SEGUINTE ? comeca.plusMonths(1) : comeca, diaInicio);

    }

    /** Mês em que o ciclo começa. */
    public YearMonth mesInicio() {
        return YearMonth.from(inicio);
    }

    /**
     * Mês da fatura de um cartão que vence dentro do ciclo. O ciclo tem exatamente um
     * dia de cada dia do mês, então cada cartão tem uma fatura nele: no mês em que o
     * ciclo começa se vence no dia de início ou depois, senão no mês seguinte.
     */
    public YearMonth mesDaFatura(int diaVencimento) {
        return diaVencimento >= inicio.getDayOfMonth() ? mesInicio() : mesInicio().plusMonths(1);
    }

}
