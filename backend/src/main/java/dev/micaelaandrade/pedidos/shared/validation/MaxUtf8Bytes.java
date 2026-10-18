package dev.micaelaandrade.pedidos.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Limita o tamanho do texto em BYTES (UTF-8), não em caracteres.
 * Usado na senha: o BCrypt só aceita até 72 bytes, e letras com acento ou emojis
 * ocupam mais de 1 byte ("ç" = 2, um emoji = 4). Contar caracteres deixaria passar
 * uma senha que o BCrypt recusa, e o cadastro quebraria com erro 500.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "Texto longo demais.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {

        private int max;

        @Override
        public void initialize(MaxUtf8Bytes annotation) {
            this.max = annotation.value();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // null fica a cargo de @NotBlank
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }
}
