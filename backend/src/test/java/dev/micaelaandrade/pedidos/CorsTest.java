package dev.micaelaandrade.pedidos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Quais origens podem chamar a API pelo navegador. O corpo vazio faz o login responder 400
 * (validação): se a resposta for 400, a origem passou pela checagem de CORS; se for 403, foi barrada.
 */
class CorsTest extends ApiTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:5173",     // front em desenvolvimento
            "http://localhost:8080",     // Swagger local
            "https://localhost:8080",    // Swagger no GitHub Codespaces (o proxy troca o Origin por este)
            "https://localhost:5173",    // front no GitHub Codespaces
    })
    void aceita_origens_de_desenvolvimento(String origin) throws Exception {
        mvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, origin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://site-malicioso.com",
            "https://app.github.dev.site-malicioso.com",
            "https://localhost.site-malicioso.com",
            // O Codespaces reescreve o Origin para localhost; o endereço público não precisa ser liberado,
            // e liberar *.app.github.dev abriria a API para o Codespace de qualquer pessoa.
            "https://special-space-x-8080.app.github.dev",
    })
    void recusa_outras_origens(String origin) throws Exception {
        mvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, origin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
