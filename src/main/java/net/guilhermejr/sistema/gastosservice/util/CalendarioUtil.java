package net.guilhermejr.sistema.gastosservice.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

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
