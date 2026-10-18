package dev.micaelaandrade.pedidos.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Confere, em toda requisição autenticada, que o token pertence a um usuário que existe
 * e que é a MESMA pessoa para quem ele foi emitido.
 *
 * <p>A assinatura só prova que o token foi emitido por esta API. Se o banco for recriado, os IDs
 * recomeçam em 1, e um token antigo com {@code sub=1} passaria a valer para quem se cadastrasse
 * primeiro no banco novo. Por isso, além do ID, comparamos o e-mail e exigimos que o token tenha
 * sido emitido depois da criação da conta.
 */
public class ActiveUserTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2TokenValidatorResult INVALID = OAuth2TokenValidatorResult.failure(
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "O usuário do token não existe mais.", null));

    private final UserRepository users;

    public ActiveUserTokenValidator(UserRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Optional<User> user = parseId(jwt.getSubject()).flatMap(users::findById);
        if (user.isEmpty()) {
            return INVALID;
        }
        boolean sameEmail = user.get().getEmail().equals(jwt.getClaimAsString(TokenService.EMAIL_CLAIM));
        Instant issuedAt = jwt.getIssuedAt();
        boolean issuedAfterSignUp = issuedAt != null
                && !issuedAt.isBefore(user.get().getCreatedAt().truncatedTo(ChronoUnit.SECONDS));
        return sameEmail && issuedAfterSignUp ? OAuth2TokenValidatorResult.success() : INVALID;
    }

    private static Optional<Long> parseId(String subject) {
        try {
            return Optional.of(Long.valueOf(subject));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
