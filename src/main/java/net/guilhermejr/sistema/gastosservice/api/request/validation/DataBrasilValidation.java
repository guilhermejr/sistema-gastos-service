package net.guilhermejr.sistema.gastosservice.api.request.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.guilhermejr.sistema.gastosservice.api.request.validation.constraints.DataBrasil;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

public class DataBrasilValidation  implements ConstraintValidator<DataBrasil, String> {

    @Override
    public void initialize(DataBrasil constraintAnnotation) {
    }

    @Override
    public boolean isValid(String data, ConstraintValidatorContext context) {

        if (data == null) {
            return true;
        }

        try {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
            LocalDate.parse(data, dateTimeFormatter);
            return true;
        } catch (Exception ex) {
            return false;
        }

    }
}
