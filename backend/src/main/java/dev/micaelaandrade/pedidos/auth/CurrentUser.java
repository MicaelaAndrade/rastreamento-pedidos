package dev.micaelaandrade.pedidos.auth;

import dev.micaelaandrade.pedidos.order.Order.Actor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Descobre quem é o usuário logado a partir do token.
 *
 * <p>O Spring Security já garantiu que o token é autêntico e está dentro da validade. Aqui
 * confirmamos que ele aponta para um usuário que ainda existe no banco: um token antigo
 * (por exemplo, emitido antes de o banco ser recriado) responde 401, e não erro 500 ao gravar.
 * O nome vem do banco, e não do token, para o histórico usar o nome atual.
 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User user(Jwt jwt) {
        Long id;
        try {
            id = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidSessionException();
        }
        return users.findById(id).orElseThrow(InvalidSessionException::new);
    }

    public Actor actor(Jwt jwt) {
        User user = user(jwt);
        return new Actor(user.getId(), user.getName());
    }
}
