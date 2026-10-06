package net.guilhermejr.sistema.gastosservice.api.request.validation.constraints;

import net.guilhermejr.sistema.gastosservice.api.request.validation.ValorMonetarioValidation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = ValorMonetarioValidation.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValorMonetario {

    String message() default "Valor inválido.";

    /** Aceita um "-" na frente — saldo inicial de conta pode começar negativo. */
    boolean negativo() default false;

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

}
