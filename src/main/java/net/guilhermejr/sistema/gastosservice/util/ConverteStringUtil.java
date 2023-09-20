package net.guilhermejr.sistema.gastosservice.util;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

@Log4j2
@Component
public class ConverteStringUtil {

    public LocalDate toLocalDate(String data) {

        DateTimeFormatter parser = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
        return LocalDate.parse(data, parser);

    }

}
