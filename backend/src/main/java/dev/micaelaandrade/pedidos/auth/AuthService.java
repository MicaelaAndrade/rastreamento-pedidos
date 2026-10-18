package dev.micaelaandrade.pedidos.auth;

import dev.micaelaandrade.pedidos.auth.AuthDtos.AuthResponse;
import dev.micaelaandrade.pedidos.auth.AuthDtos.LoginRequest;
import dev.micaelaandrade.pedidos.auth.AuthDtos.RegisterRequest;
import dev.micaelaandrade.pedidos.auth.AuthDtos.UserResponse;
import dev.micaelaandrade.pedidos.auth.TokenService.IssuedToken;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {

    /** Mensagem única para e-mail inexistente e senha errada: não revela quais e-mails têm conta. */
    static final String INVALID_CREDENTIALS = "E-mail ou senha inválidos.";

    /** Limite do BCrypt. O cadastro já recusa senhas maiores (ver RegisterRequest). */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Clock clock;
    private final String dummyHash;
    private final TransactionTemplate transactions;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens, Clock clock,
                       PlatformTransactionManager transactionManager) {
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
        // Usado quando o e-mail não existe, para o login levar o mesmo tempo nos dois casos.
        this.dummyHash = passwordEncoder.encode("senha-que-nunca-sera-usada");
    }

    /*
     * O hash da senha (BCrypt, ~80 ms de CPU) é calculado FORA de transação: o pool tem uma única conexão
     * (SQLite), e segurá-la durante o hash travaria as outras rotas. Só a parte rápida, "e-mail já existe?"
     * + gravar, fica numa transação, para dois cadastros simultâneos com o mesmo e-mail não passarem os
     * dois pela checagem.
     */
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(); // atalho: evita calcular o hash à toa
        }
        String hash = passwordEncoder.encode(request.password());
        User user = new User(request.name(), email, hash, Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));
        try {
            transactions.executeWithoutResult(tx -> {
                if (users.existsByEmail(email)) {
                    throw new EmailAlreadyRegisteredException();
                }
                users.saveAndFlush(user);
            });
        } catch (DataAccessException e) {
            // Se mesmo assim o índice único do banco recusar (ex.: com mais de uma conexão no pool),
            // é o mesmo caso: e-mail já cadastrado. O SQLite sinaliza isso como erro genérico de JPA,
            // por isso a confirmação é feita consultando de novo.
            if (users.existsByEmail(email)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw e;
        }
        return authenticated(user);
    }

    public AuthResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmail(normalize(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        // O BCrypt só compara os primeiros 72 bytes: sem esta regra, "senha + qualquer coisa" entraria.
        // A comparação roda mesmo assim, para a resposta levar o mesmo tempo nos dois casos.
        boolean tooLong = request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
        // (Uma senha vazia não serviria: o encoder responde "não confere" na hora, sem calcular o hash.)
        String candidate = tooLong ? "senha-longa-demais-nunca-confere" : request.password();
        boolean passwordMatches = passwordEncoder.matches(candidate, hash) && !tooLong;
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
