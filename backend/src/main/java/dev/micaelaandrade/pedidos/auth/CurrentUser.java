package dev.micaelaandrade.pedidos.auth;

import dev.micaelaandrade.pedidos.order.Order.Actor;
import org.springframework.security.oauth2.jwt.Jwt;

/** Lê do token quem é o usuário logado. */
public final class CurrentUser {

    public static final String NAME_CLAIM = "name";

    private CurrentUser() {
    }

    public static Actor from(Jwt jwt) {
        return new Actor(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(NAME_CLAIM));
    }
}
