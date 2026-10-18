package dev.micaelaandrade.pedidos.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;

/**
 * Leitura de JSON estrita: o valor precisa vir no tipo certo, sem conversões silenciosas.
 * Por padrão o Jackson é tolerante e mudaria o pedido sem avisar:
 * <ul>
 *   <li>{@code "quantity": 1.5} viraria 1;</li>
 *   <li>{@code "quantity": "2"} (texto) viraria 2;</li>
 *   <li>{@code "status": 4} seria lido pela posição no enum (CANCELADO).</li>
 * </ul>
 * Com esta configuração, esses casos respondem 400.
 */
@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer strictJson() {
        return builder -> builder
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
    }
}
