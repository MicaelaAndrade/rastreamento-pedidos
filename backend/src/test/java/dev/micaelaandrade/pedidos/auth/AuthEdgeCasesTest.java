package dev.micaelaandrade.pedidos.auth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.micaelaandrade.pedidos.ApiTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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

    private String token(String subject, boolean withName) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(TokenService.ISSUER)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600));
        if (withName) {
            claims.claim("name", "Alguém");
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    @Test
    void token_de_usuario_que_nao_existe_responde_401_ao_criar_pedido_e_no_me() throws Exception {
        String token = token("999999", true);

        mvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", containsString("Faça login novamente")));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_com_subject_que_nao_e_numero_responde_401() throws Exception {
        mvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("abc", true)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_sem_o_nome_usa_o_nome_do_banco_no_historico() throws Exception {
        String body = register(uniqueEmail(), "senha-segura");
        String userId = String.valueOf((Integer) com.jayway.jsonpath.JsonPath.read(body, "$.user.id"));

        mvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(userId, false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusHistory[0].changedBy").value("Mica"));
    }
}
