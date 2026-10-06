package net.guilhermejr.sistema.gastosservice.api.request.validation;

import net.guilhermejr.sistema.gastosservice.api.request.validation.constraints.ValorMonetario;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValorMonetarioValidation implements ConstraintValidator<ValorMonetario, String> {

    private boolean negativo;

    @Override
    public void initialize(ValorMonetario constraintAnnotation) {
        this.negativo = constraintAnnotation.negativo();
    }

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {

        if (valor == null) {
            return true;
        }

        if (negativo && valor.startsWith("-")) {
            valor = valor.substring(1);
        }

        return valor.matches("^([1-9]{1}[\\d]{0,2}(\\.[\\d]{3})*(\\,[\\d]{0,2})?|[1-9]{1}[\\d]{0,}(\\,[\\d]{0,2})?|0(\\,[\\d]{0,2})?|(\\,[\\d]{1,2})?)$");

    }
}
