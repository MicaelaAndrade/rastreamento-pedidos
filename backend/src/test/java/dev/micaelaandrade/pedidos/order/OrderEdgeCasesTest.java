package dev.micaelaandrade.pedidos.order;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.micaelaandrade.pedidos.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Casos de borda de pedidos encontrados na revisão. */
class OrderEdgeCasesTest extends ApiTest {

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = registerAndGetToken("Mica");
    }

    private static String order(String phone, String item) {
        return """
                {"customer": {"name": "Ana", "phone": "%s"},
                 "deliveryAddress": {"street": "Rua A", "number": "1", "neighborhood": "Centro",
                                     "city": "Franca", "zipCode": "14400-000"},
                 "items": [%s]}
                """.formatted(phone, item);
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions createItem(String item) throws Exception {
        return create(order("(16) 99999-0000", item));
    }

    private int createValid() throws Exception {
        String body = createItem("{\"name\": \"X\", \"quantity\": 1, \"unitPrice\": 10}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions patchStatus(int id, String json) throws Exception {
        return mvc.perform(patch("/api/orders/{id}/status", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // ---------- Tipos estritos: nada de conversão silenciosa ----------

    @ParameterizedTest(name = "quantity = {0}")
    @ValueSource(strings = {"1.5", "\"2\"", "1e2"})
    void quantidade_precisa_ser_numero_inteiro(String quantity) throws Exception {
        createItem("{\"name\": \"X\", \"quantity\": %s, \"unitPrice\": 10}".formatted(quantity))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("items[0].quantity")))
                .andExpect(jsonPath("$.detail", containsString("número inteiro")));
    }

    @Test
    void preco_enviado_como_texto_e_recusado() throws Exception {
        createItem("{\"name\": \"X\", \"quantity\": 1, \"unitPrice\": \"10\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("items[0].unitPrice")));
    }

    @ParameterizedTest(name = "status = {0}")
    @ValueSource(strings = {"4", "\"1\"", "\"cancelado\""})
    void status_so_aceita_o_nome_exato(String status) throws Exception {
        int id = createValid();

        patchStatus(id, "{\"status\": %s}".formatted(status))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("RECEBIDO, EM_PREPARO")));
    }

    // ---------- Preço ----------

    @Test
    void preco_zero_e_recusado() throws Exception {
        createItem("{\"name\": \"X\", \"quantity\": 1, \"unitPrice\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", contains("O preço de um item precisa ser maior que zero.")));
    }

    @Test
    void preco_alto_demais_tem_mensagem_de_valor_maximo_e_nao_de_casas_decimais() throws Exception {
        createItem("{\"name\": \"X\", \"quantity\": 1, \"unitPrice\": 1e10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", contains("O preço máximo de um item é R$ 9.999.999,99.")));
    }

    @Test
    void preco_com_tres_casas_decimais_e_recusado() throws Exception {
        createItem("{\"name\": \"X\", \"quantity\": 1, \"unitPrice\": 10.123}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", contains("O preço deve ter no máximo 2 casas decimais.")));
    }

    // ---------- Telefone e mensagens ----------

    @ParameterizedTest(name = "telefone = {0}")
    @ValueSource(strings = {"(16) 999", "((((((((", "16 99999-00001234"})
    void telefone_incompleto_ou_invalido_e_recusado(String phone) throws Exception {
        create(order(phone, "{\"name\": \"X\", \"quantity\": 1, \"unitPrice\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("customer.phone")));
    }

    @ParameterizedTest(name = "telefone = {0}")
    @ValueSource(strings = {"(16) 3333-4444", "16999990000", "+55 16 99999-0000"})
    void telefone_fixo_celular_e_com_codigo_do_pais_sao_aceitos(String phone) throws Exception {
        create(order(phone, "{\"name\": \"X\", \"quantity\": 1, \"unitPrice\": 10}"))
                .andExpect(status().isCreated());
    }

    @Test
    void item_nulo_tem_mensagem_em_portugues() throws Exception {
        createItem("null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", contains("Item vazio na lista de itens.")));
    }

    @Test
    void campo_longo_demais_diz_qual_e_o_limite() throws Exception {
        create(order("(16) 99999-0000", "{\"name\": \"X\", \"quantity\": 1, \"unitPrice\": 10}")
                .replace("\"Rua A\"", "\"" + "R".repeat(201) + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", contains("A rua pode ter no máximo 200 caracteres.")));
    }

    // ---------- Versão do pedido (tela desatualizada) ----------

    @Test
    void mudanca_com_a_versao_atual_funciona_e_incrementa_a_versao() throws Exception {
        int id = createValid();

        patchStatus(id, "{\"status\": \"EM_PREPARO\", \"expectedVersion\": 0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_PREPARO"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void mudanca_feita_sobre_versao_antiga_responde_409_e_nao_altera_o_pedido() throws Exception {
        int id = createValid();
        // Pessoa A inicia o preparo (versão 0 → 1)
        patchStatus(id, "{\"status\": \"EM_PREPARO\", \"expectedVersion\": 0}").andExpect(status().isOk());

        // Pessoa B ainda vê a versão 0 e tenta cancelar: a regra permitiria (EM_PREPARO → CANCELADO),
        // mas ela decidiu olhando um pedido que já mudou.
        patchStatus(id, "{\"status\": \"CANCELADO\", \"expectedVersion\": 0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("foi alterado por outra pessoa")))
                .andExpect(jsonPath("$.detail", containsString("Em preparo")));
    }

    @Test
    void sem_expected_version_a_mudanca_continua_funcionando() throws Exception {
        int id = createValid();

        patchStatus(id, "{\"status\": \"EM_PREPARO\"}").andExpect(status().isOk());
    }
}
