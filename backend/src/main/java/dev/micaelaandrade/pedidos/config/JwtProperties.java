package dev.micaelaandrade.pedidos.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do JWT (prefixo {@code app.jwt} no application.yml).
 *
 * @param secret     chave do HMAC-SHA256; precisa ter pelo menos 32 bytes
 * @param expiration validade do token
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret precisa ter pelo menos 32 caracteres (HS256).");
        }
        if (expiration == null) {
            expiration = Duration.ofHours(8);
        }
    }
}
