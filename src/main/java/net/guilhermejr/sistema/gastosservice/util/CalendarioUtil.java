package net.guilhermejr.sistema.gastosservice.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Regras de data e de divisão de valores, sem acesso a banco — tudo o que decide em
 * qual fatura cai uma compra e em que dia cai uma parcela ou uma despesa fixa.
 */
public final class CalendarioUtil {

    private CalendarioUtil() {
    }

    /** O dia no mês, recuado para o último dia quando o mês é mais curto (31 → 30, 28). */
    public static LocalDate diaNoMes(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    public static LocalDate vencimentoNoMes(YearMonth mes, int diaVencimento) {
        return diaNoMes(mes, diaVencimento);
    }

    /** A fatura fecha {@code diasFechamento} dias antes do vencimento. */
    public static LocalDate fechamento(LocalDate vencimento, int diasFechamento) {
        return vencimento.minusDays(diasFechamento);
    }

    /**
     * Vencimento da fatura em que entra uma compra feita em {@code dataCompra}.
     *
     * <p>A compra entra na primeira fatura que ainda não fechou: precisa ser
     * <strong>anterior</strong> à data de fechamento. Comprar no próprio dia do
     * fechamento já cai na fatura seguinte — o "melhor dia de compra".
     *
     * <p>Nenhuma fatura de um mês anterior ao da compra pode servir (ela fechou antes
     * do vencimento, que já passou), então a busca começa no mês da compra.
     */
    public static LocalDate vencimentoDaCompra(LocalDate dataCompra, int diaVencimento, int diasFechamento) {

        YearMonth mes = YearMonth.from(dataCompra);
        while (true) {
            LocalDate vencimento = vencimentoNoMes(mes, diaVencimento);
            if (dataCompra.isBefore(fechamento(vencimento, diasFechamento))) {
                return vencimento;
            }
            mes = mes.plusMonths(1);
        }

    }

    /**
     * O vencimento que o banco informou é o do dia configurado: o próprio dia ou, quando
     * ele cai em fim de semana ou feriado, o próximo dia útil — a fatura de 06/09/2026
     * (domingo, com o feriado de 07/09) venceu em 08/09. O adiamento pode virar o mês
     * (dia 31 num sábado), então o mês anterior também é conferido.
     */
    public static boolean mesmoVencimento(LocalDate vencimentoBanco, int diaVencimento) {

        YearMonth mes = YearMonth.from(vencimentoBanco);
        for (YearMonth candidato : List.of(mes, mes.minusMonths(1))) {
            LocalDate configurado = vencimentoNoMes(candidato, diaVencimento);
            if (vencimentoBanco.equals(configurado) || vencimentoBanco.equals(proximoDiaUtil(configurado))) {
                return true;
            }
        }
        return false;

    }

    /** A própria data, se for dia útil; senão, o primeiro dia útil depois dela. */
    public static LocalDate proximoDiaUtil(LocalDate data) {

        LocalDate dia = data;
        while (!diaUtil(dia)) {
            dia = dia.plusDays(1);
        }
        return dia;

    }

    /**
     * Dia em que os bancos funcionam: nem fim de semana nem feriado nacional — os fixos e
     * os que dependem da Páscoa (Carnaval, Sexta-feira Santa e Corpus Christi, em que os
     * bancos também fecham). Feriados municipais ficam de fora.
     */
    public static boolean diaUtil(LocalDate data) {

        if (data.getDayOfWeek() == DayOfWeek.SATURDAY || data.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        if (FERIADOS_FIXOS.contains(MonthDay.from(data))) {
            return false;
        }
        LocalDate pascoa = pascoa(data.getYear());
        return !List.of(pascoa.minusDays(48), pascoa.minusDays(47), pascoa.minusDays(2), pascoa.plusDays(60)).contains(data);

    }

    private static final Set<MonthDay> FERIADOS_FIXOS = Set.of(MonthDay.of(1, 1), MonthDay.of(4, 21), MonthDay.of(5, 1),
            MonthDay.of(9, 7), MonthDay.of(10, 12), MonthDay.of(11, 2), MonthDay.of(11, 15), MonthDay.of(11, 20), MonthDay.of(12, 25));

    /** Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher, calendário gregoriano). */
    static LocalDate pascoa(int ano) {

        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(ano, mes, dia);

    }

    /**
     * Data da n-ésima repetição mensal (0 = a própria data), sempre no dia original:
     * uma série que começa em 31/01 cai em 28/02 e volta para 31/03, em vez de
     * ficar presa no dia 28 como aconteceria somando um mês por vez.
     */
    public static LocalDate mesesDepois(LocalDate inicio, int dia, int meses) {
        return diaNoMes(YearMonth.from(inicio).plusMonths(meses), dia);
    }

    /**
     * Divide o total em parcelas iguais, em centavos. O resto da divisão vai para a
     * primeira parcela, então a soma das parcelas é sempre exatamente o total
     * (100,00 em 3 → 33,34 + 33,33 + 33,33).
     */
    public static List<BigDecimal> dividirEmParcelas(BigDecimal total, int quantidade) {

        BigDecimal parcela = total.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.DOWN);
        BigDecimal resto = total.subtract(parcela.multiply(BigDecimal.valueOf(quantidade)));

        List<BigDecimal> parcelas = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            parcelas.add(i == 0 ? parcela.add(resto) : parcela);
        }
        return parcelas;

    }

}
