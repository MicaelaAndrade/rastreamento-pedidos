package dev.micaelaandrade.pedidos.auth;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.micaelaandrade.pedidos.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthApiTest extends ApiTest {

    private void register(String name, String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "email": "%s", "password": "%s"}
                                """.formatted(name, email, password)))
                .andExpect(status().isCreated());
    }

    @Test
    void cadastro_devolve_token_e_dados_do_usuario_sem_a_senha() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Mica", "email": "Mica.Cadastro@Exemplo.com", "password": "senha-segura"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(not(emptyString())))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.user.name").value("Mica"))
                // e-mail normalizado em minúsculas
                .andExpect(jsonPath("$.user.email").value("mica.cadastro@exemplo.com"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void nao_permite_dois_cadastros_com_o_mesmo_email() throws Exception {
        register("Mica", "duplicado@exemplo.com", "senha-segura");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Outra", "email": "DUPLICADO@exemplo.com", "password": "outra-senha"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma conta com este e-mail."));
    }

    @Test
    void cadastro_valida_os_campos() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "email": "nao-e-email", "password": "curta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")));
    }

    @Test
    void login_com_senha_correta_devolve_token() throws Exception {
        register("Mica", "login@exemplo.com", "senha-segura");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "Login@Exemplo.com", "password": "senha-segura"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not(emptyString())))
                .andExpect(jsonPath("$.user.email").value("login@exemplo.com"));
    }

    @Test
    void login_com_senha_errada_ou_email_inexistente_da_a_mesma_resposta() throws Exception {
        register("Mica", "senha-errada@exemplo.com", "senha-segura");

        for (String credentials : new String[] {
                """
                {"email": "senha-errada@exemplo.com", "password": "outra-senha"}""",
                """
                {"email": "ninguem@exemplo.com", "password": "senha-segura"}"""}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
        }
    }

    @Test
    void me_devolve_o_usuario_do_token() throws Exception {
        String token = registerAndGetToken("Usuária do Token");

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Usuária do Token"));
    }

    @Test
    void rotas_protegidas_exigem_token() throws Exception {
        mvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_invalido_e_recusado() throws Exception {
        String token = registerAndGetToken("Mica");
        String adulterado = token.substring(0, token.length() - 4) + "abcd";

        mvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(adulterado)))
                .andExpect(status().isUnauthorized());
    }
}
