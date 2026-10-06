package net.guilhermejr.sistema.gastosservice.util;

import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

@Log4j2
@Component
public class ConverteStringUtil {

    private static final DateTimeFormatter DATA_BRASIL = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public LocalDate toLocalDate(String data) {

        return LocalDate.parse(data, DATA_BRASIL);

    }

    /** "1.234,56" → 1234.56. O formato já foi conferido por @ValorMonetario. */
    public BigDecimal toBigDecimal(String valor) {

        try {
            return new BigDecimal(valor.replace(".", "").replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            log.error("Erro ao converter valor monetário: {}", valor);
            throw new ExceptionDefault("Valor inválido: " + valor);
        }

    }

    /** Mesmo que toBigDecimal, mas recusa zero e negativos — nenhum lançamento vale 0. */
    public BigDecimal toValorPositivo(String valor) {

        BigDecimal convertido = toBigDecimal(valor);
        if (convertido.signum() <= 0) {
            throw new ExceptionDefault("O valor precisa ser maior que zero.");
        }
        return convertido;

    }

    public YearMonth toYearMonth(Integer ano, Integer mes) {

        try {
            return YearMonth.of(ano, mes);
        } catch (DateTimeException ex) {
            throw new ExceptionDefault("Mês inválido: " + mes + "/" + ano);
        }

    }

    /** 1234.5 → "R$ 1.234,50", para mensagens de erro. */
    public String formatarMoeda(BigDecimal valor) {

        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor).replace('\u00a0', ' ');

    }

}
