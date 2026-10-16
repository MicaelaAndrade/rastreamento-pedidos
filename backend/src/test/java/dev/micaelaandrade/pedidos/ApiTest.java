package dev.micaelaandrade.pedidos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dos testes de integração: sobe a aplicação inteira (segurança, banco SQLite em memória,
 * migrations do Flyway) e faz requisições HTTP de verdade pelo MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTest {

    @Autowired
    protected MockMvc mvc;

    /** Cadastra um usuário novo (e-mail único a cada chamada) e devolve o token dele. */
    protected String registerAndGetToken(String name) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@exemplo.com";
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "email": "%s", "password": "senha-segura"}
                                """.formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
