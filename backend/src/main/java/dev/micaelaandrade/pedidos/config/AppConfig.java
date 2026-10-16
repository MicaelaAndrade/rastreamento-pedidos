package dev.micaelaandrade.pedidos.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@OpenAPIDefinition(
        info = @Info(title = "Rastreamento de Pedidos", version = "1.0",
                description = "Cadastre-se ou faça login em /api/auth, clique em Authorize e cole o token."),
        security = @SecurityRequirement(name = "bearerAuth"),
        // Endereço relativo: o Swagger chama a API pelo mesmo endereço em que foi aberto
        // (localhost, Codespaces ou outro servidor), em vez de um "http://localhost:8080" fixo.
        servers = @Server(url = "/", description = "Esta API"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class AppConfig {

    /** Relógio injetável: nos testes dá para fixar o horário. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
