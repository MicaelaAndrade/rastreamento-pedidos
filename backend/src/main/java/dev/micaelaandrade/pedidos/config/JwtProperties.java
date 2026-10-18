package dev.micaelaandrade.pedidos.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do JWT (prefixo {@code app.jwt} no application.yml).
 *
 * @param secret     chave do HMAC-SHA256; precisa ter pelo menos 32 bytes
 * @param expiration validade do token
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration) {

    /** Chave usada quando JWT_SECRET não é definido. Pública no repositório: serve só para rodar localmente. */
    public static final String DEV_SECRET = "chave-de-desenvolvimento-troque-em-producao-0123456789";

    private static final Logger log = LoggerFactory.getLogger(JwtProperties.class);

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret precisa ter pelo menos 32 caracteres (HS256).");
        }
        if (expiration == null) {
            expiration = Duration.ofHours(8);
        }
        if (DEV_SECRET.equals(secret)) {
            log.warn("JWT_SECRET não definido: usando a chave de desenvolvimento, que é pública no repositório. "
                    + "Qualquer pessoa poderia gerar tokens válidos. Defina JWT_SECRET fora do ambiente local.");
        }
    }
}
