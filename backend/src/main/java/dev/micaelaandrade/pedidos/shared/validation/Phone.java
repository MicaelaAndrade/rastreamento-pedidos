package dev.micaelaandrade.pedidos.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

/**
 * Telefone brasileiro com DDD, com ou sem formatação e com ou sem +55.
 * Aceita "(16) 99999-0000", "16999990000", "+55 16 3333-4444".
 * Recusa números incompletos como "(16) 999".
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Phone.Validator.class)
public @interface Phone {

    String message() default "Telefone inválido. Informe DDD e número, por exemplo (16) 99999-0000.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Phone, String> {

        /** Só dígitos e os símbolos usados para formatar. */
        private static final Pattern ALLOWED = Pattern.compile("^[0-9()+\\-\\s]+$");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true; // vazio fica a cargo de @NotBlank
            }
            if (!ALLOWED.matcher(value).matches()) {
                return false;
            }
            String digits = value.replaceAll("\\D", "");
            if (digits.startsWith("55") && digits.length() > 11) {
                digits = digits.substring(2); // código do Brasil
            }
            // DDD (2 dígitos) + fixo (8) ou celular (9)
            return digits.length() == 10 || digits.length() == 11;
        }
    }
}
