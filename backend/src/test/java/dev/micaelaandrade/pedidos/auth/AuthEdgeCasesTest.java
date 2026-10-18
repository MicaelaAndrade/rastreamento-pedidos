package dev.micaelaandrade.pedidos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.micaelaandrade.pedidos.ApiTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Casos de borda de autenticação encontrados na revisão. */
class AuthEdgeCasesTest extends ApiTest {

    private static final String VALID_ORDER = """
            {"customer": {"name": "Ana", "phone": "(16) 99999-0000"},
             "deliveryAddress": {"street": "Rua A", "number": "1", "neighborhood": "Centro",
                                 "city": "Franca", "zipCode": "14400-000"},
             "items": [{"name": "X", "quantity": 1, "unitPrice": 10}]}
            """;

    @Autowired
    private JwtEncoder encoder;

    private static String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@exemplo.com";
    }

    private String register(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Mica", "email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void senha_com_acentos_acima_de_72_bytes_responde_400_e_nao_500() throws Exception {
        String password = "ç".repeat(40); // 40 caracteres, 80 bytes

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Mica", "email": "%s", "password": "%s"}
                                """.formatted(uniqueEmail(), password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message", containsString("72 bytes")));
    }

    @Test
    void senha_com_acentos_dentro_do_limite_funciona_no_cadastro_e_no_login() throws Exception {
        String email = uniqueEmail();
        String password = "ç".repeat(36); // exatamente 72 bytes
        register(email, password);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk());
    }

    @Test
    void senha_vazia_gera_um_unico_erro_para_o_campo() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Mica", "email": "%s", "password": ""}
                                """.formatted(uniqueEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("password")));
    }

    @Test
    void email_com_espacos_nas_pontas_e_aceito_no_cadastro() throws Exception {
        String email = uniqueEmail();

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  Mica  ", "email": "  %s ", "password": "senha-segura"}
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.name").value("Mica"))
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void metodo_errado_em_rota_publica_responde_405_e_nao_401() throws Exception {
        mvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void json_malformado_tem_mensagem_neutra() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", not(containsString("Status"))));
    }

    // ---------- Tokens autênticos, mas com dados que não batem com o banco ----------

    /** Gera um token assinado com a chave da API, com os dados que o teste quiser. */
    private String token(String subject, String email, Instant issuedAt, boolean withName) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(TokenService.ISSUER)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(now().plusSeconds(3600)); // sempre dentro da validade: o teste é sobre outra regra
        if (email != null) {
            claims.claim("email", email);
        }
        if (withName) {
            claims.claim("name", "Alguém");
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private record Registered(String id, String email) {
    }

    private Registered registerUser() throws Exception {
        String email = uniqueEmail();
        String body = register(email, "senha-segura");
        return new Registered(String.valueOf((Integer) JsonPath.read(body, "$.user.id")), email);
    }

    private void expectUnauthorizedEverywhere(String token) throws Exception {
        mvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_de_usuario_que_nao_existe_e_recusado_em_todas_as_rotas_inclusive_consultas() throws Exception {
        expectUnauthorizedEverywhere(token("999999", "ninguem@exemplo.com", now(), true));
    }

    @Test
    void token_com_subject_que_nao_e_numero_e_recusado() throws Exception {
        expectUnauthorizedEverywhere(token("abc", "ninguem@exemplo.com", now(), true));
    }

    @Test
    void token_com_id_reaproveitado_por_outra_pessoa_e_recusado() throws Exception {
        // Banco recriado: o ID existe, mas pertence a outra pessoa (e-mail diferente).
        Registered user = registerUser();
        expectUnauthorizedEverywhere(token(user.id(), "pessoa-antiga@exemplo.com", now(), true));
    }

    @Test
    void token_emitido_antes_de_a_conta_existir_e_recusado() throws Exception {
        // Mesmo e-mail cadastrado de novo num banco recriado: o token antigo é anterior à conta.
        Registered user = registerUser();
        expectUnauthorizedEverywhere(token(user.id(), user.email(), now().minus(1, ChronoUnit.DAYS), true));
    }

    @Test
    void token_sem_o_nome_usa_o_nome_do_banco_no_historico() throws Exception {
        Registered user = registerUser();

        mvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(user.id(), user.email(), now(), false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusHistory[0].changedBy").value("Mica"));
    }

    // ---------- Senha no login e cadastro simultâneo ----------

    @Test
    void login_recusa_senha_com_bytes_a_mais_depois_do_limite_do_bcrypt() throws Exception {
        String email = uniqueEmail();
        String password = "a".repeat(72);
        register(email, password);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password + "b")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)))
                .andExpect(status().isOk());
    }

    @Test
    void cadastros_simultaneos_com_o_mesmo_email_criam_uma_conta_e_os_outros_recebem_409() throws Exception {
        String email = uniqueEmail();
        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    start.await(); // todos disparam juntos
                    return mvc.perform(post("/api/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"name\": \"Mica\", \"email\": \"%s\", \"password\": \"senha-segura\"}"
                                            .formatted(email)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsOnly(201, 409);
            assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
