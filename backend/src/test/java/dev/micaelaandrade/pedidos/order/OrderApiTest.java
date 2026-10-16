package dev.micaelaandrade.pedidos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.micaelaandrade.pedidos.ApiTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class OrderApiTest extends ApiTest {

    private static final String VALID_ORDER = """
            {
              "customer": {"name": "Ana Souza", "phone": "(16) 99999-0000"},
              "deliveryAddress": {
                "street": "Rua das Flores", "number": "120", "complement": "Apto 3",
                "neighborhood": "Centro", "city": "Franca", "zipCode": "14400-000"
              },
              "items": [
                {"name": "X-Burger", "quantity": 2, "unitPrice": 25.90},
                {"name": "Refrigerante", "quantity": 1, "unitPrice": 7.5}
              ]
            }
            """;

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = registerAndGetToken("Mica");
    }

    private ResultActions createOrder(String json) throws Exception {
        return mvc.perform(post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private int createValidOrder() throws Exception {
        String body = createOrder(VALID_ORDER).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions changeStatus(int id, String newStatus) throws Exception {
        return mvc.perform(patch("/api/orders/{id}/status", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + newStatus + "\"}"));
    }

    @Test
    void cria_pedido_como_recebido_com_total_calculado() throws Exception {
        createOrder(VALID_ORDER)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/orders/\\d+")))
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.customer.name").value("Ana Souza"))
                .andExpect(jsonPath("$.deliveryAddress.city").value("Franca"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].subtotal").value(51.80))
                .andExpect(jsonPath("$.total").value(59.30))
                .andExpect(jsonPath("$.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.statusHistory[0].changedBy").value("Mica"))
                .andExpect(jsonPath("$.allowedNextStatuses", contains("EM_PREPARO", "CANCELADO")));
    }

    @Test
    void busca_pedido_por_id_com_os_mesmos_dados_gravados() throws Exception {
        int id = createValidOrder();

        mvc.perform(get("/api/orders/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.items[1].unitPrice").value(7.50))
                .andExpect(jsonPath("$.total").value(59.30))
                .andExpect(jsonPath("$.deliveryAddress.complement").value("Apto 3"));
    }

    @Test
    void pedido_inexistente_devolve_404() throws Exception {
        mvc.perform(get("/api/orders/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pedido #999999 não encontrado."));
    }

    @Test
    void lista_pedidos_do_mais_recente_para_o_mais_antigo() throws Exception {
        int first = createValidOrder();
        int second = createValidOrder();

        String body = mvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Integer> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids).containsSubsequence(second, first);
    }

    @Test
    void filtra_a_lista_por_status() throws Exception {
        int id = createValidOrder();
        changeStatus(id, "EM_PREPARO").andExpect(status().isOk());

        mvc.perform(get("/api/orders").param("status", "EM_PREPARO")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", everyItem(is("EM_PREPARO"))))
                .andExpect(jsonPath("$[*].id", hasItem(id)));
    }

    @Test
    void percorre_o_fluxo_completo_e_registra_o_historico() throws Exception {
        int id = createValidOrder();

        changeStatus(id, "EM_PREPARO").andExpect(status().isOk());
        changeStatus(id, "SAIU_PARA_ENTREGA").andExpect(status().isOk());
        changeStatus(id, "ENTREGUE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENTREGUE"))
                .andExpect(jsonPath("$.allowedNextStatuses", hasSize(0)))
                .andExpect(jsonPath("$.statusHistory[*].status",
                        contains("RECEBIDO", "EM_PREPARO", "SAIU_PARA_ENTREGA", "ENTREGUE")));
    }

    @Test
    void transicao_invalida_devolve_409_e_mantem_o_status() throws Exception {
        int id = createValidOrder();

        changeStatus(id, "ENTREGUE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("O pedido #%d está \"Recebido\" e não pode ir para \"Entregue\".".formatted(id)));

        mvc.perform(get("/api/orders/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.statusHistory", hasSize(1)));
    }

    @Test
    void nao_cancela_pedido_que_ja_saiu_para_entrega() throws Exception {
        int id = createValidOrder();
        changeStatus(id, "EM_PREPARO");
        changeStatus(id, "SAIU_PARA_ENTREGA");

        changeStatus(id, "CANCELADO").andExpect(status().isConflict());
    }

    @Test
    void status_desconhecido_devolve_400() throws Exception {
        int id = createValidOrder();

        changeStatus(id, "VOANDO").andExpect(status().isBadRequest());
    }

    @Test
    void mudar_status_de_pedido_inexistente_devolve_404() throws Exception {
        changeStatus(999_999, "EM_PREPARO").andExpect(status().isNotFound());
    }

    @Test
    void criacao_valida_os_campos_e_diz_quais_estao_errados() throws Exception {
        createOrder("""
                {
                  "customer": {"name": "", "phone": "abc"},
                  "deliveryAddress": {"street": "Rua", "number": "1", "neighborhood": "Centro",
                                      "city": "Franca", "zipCode": "123"},
                  "items": [{"name": "X-Burger", "quantity": 0, "unitPrice": -1}]
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("customer.name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("customer.phone")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("deliveryAddress.zipCode")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("items[0].quantity")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("items[0].unitPrice")));
    }

    @Test
    void nao_cria_pedido_sem_itens() throws Exception {
        createOrder("""
                {
                  "customer": {"name": "Ana", "phone": "16999990000"},
                  "deliveryAddress": {"street": "Rua", "number": "1", "neighborhood": "Centro",
                                      "city": "Franca", "zipCode": "14400-000"},
                  "items": []
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }
}
