package dev.micaelaandrade.pedidos.auth;

import dev.micaelaandrade.pedidos.shared.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Informe seu nome.")
            @Size(max = 120, message = "O nome pode ter no máximo 120 caracteres.")
            String name,
            @NotBlank(message = "Informe seu e-mail.")
            @Email(message = "E-mail inválido.")
            @Size(max = 254, message = "O e-mail pode ter no máximo 254 caracteres.")
            String email,
            @NotNull(message = "Informe uma senha.")
            @Size(min = 8, message = "A senha precisa ter pelo menos 8 caracteres.")
            // O BCrypt aceita até 72 BYTES; letras com acento e emojis ocupam mais de 1 byte cada.
            @MaxUtf8Bytes(value = 72, message = "A senha é longa demais. Use no máximo 72 bytes "
                    + "(letras com acento e emojis ocupam mais de um).")
            String password) {

        /** Tira espaços das pontas ANTES da validação (a senha fica como foi digitada). */
        public RegisterRequest {
            name = strip(name);
            email = strip(email);
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Informe seu e-mail.") String email,
            @NotEmpty(message = "Informe sua senha.") String password) {

        public LoginRequest {
            email = strip(email);
        }
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    public record UserResponse(Long id, String name, String email) {

        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail());
        }
    }

    public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
    }
}
