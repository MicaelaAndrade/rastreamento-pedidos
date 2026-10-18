package dev.micaelaandrade.pedidos.auth;

import dev.micaelaandrade.pedidos.auth.AuthDtos.AuthResponse;
import dev.micaelaandrade.pedidos.auth.AuthDtos.LoginRequest;
import dev.micaelaandrade.pedidos.auth.AuthDtos.RegisterRequest;
import dev.micaelaandrade.pedidos.auth.AuthDtos.UserResponse;
import dev.micaelaandrade.pedidos.auth.TokenService.IssuedToken;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    /** Mensagem única para e-mail inexistente e senha errada: não revela quais e-mails têm conta. */
    static final String INVALID_CREDENTIALS = "E-mail ou senha inválidos.";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
        // Usado quando o e-mail não existe, para o login levar o mesmo tempo nos dois casos.
        this.dummyHash = passwordEncoder.encode("senha-que-nunca-sera-usada");
    }

    /*
     * Sem @Transactional de propósito, em register e login: o BCrypt leva ~80 ms de CPU, e o pool tem
     * uma única conexão (SQLite). Dentro de uma transação, cada cadastro ou login seguraria essa conexão
     * durante o hash e travaria as outras rotas. Assim, cada consulta abre e fecha a própria transação.
     */
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        String hash = passwordEncoder.encode(request.password());
        User user = new User(request.name(), email, hash, Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Dois cadastros simultâneos com o mesmo e-mail: o índice único do banco segura o segundo.
            throw new EmailAlreadyRegisteredException();
        }
        return authenticated(user);
    }

    public AuthResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmail(normalize(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        return authenticated(user.get());
    }

    private AuthResponse authenticated(User user) {
        IssuedToken token = tokens.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    private static String normalize(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
