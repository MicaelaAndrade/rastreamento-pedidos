package dev.micaelaandrade.pedidos.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
            @Size(max = 254)
            String email,
            @NotBlank(message = "Informe uma senha.")
            // 72 é o limite do BCrypt: bytes além disso seriam ignorados sem aviso.
            @Size(min = 8, max = 72, message = "A senha precisa ter entre 8 e 72 caracteres.")
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Informe seu e-mail.") String email,
            @NotBlank(message = "Informe sua senha.") String password) {
    }

    public record UserResponse(Long id, String name, String email) {

        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail());
        }
    }

    public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
    }
}
