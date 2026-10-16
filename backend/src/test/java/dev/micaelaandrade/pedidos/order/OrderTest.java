package dev.micaelaandrade.pedidos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.micaelaandrade.pedidos.order.Order.Actor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Actor MICA = new Actor(1L, "Mica");
    private static final Actor JOAO = new Actor(2L, "João");
    private static final Instant T0 = Instant.parse("2026-10-16T18:00:00Z");

    private static Order newOrder() {
        return Order.place(
                new Customer("Ana Souza", "(16) 99999-0000"),
                new DeliveryAddress("Rua das Flores", "120", null, "Centro", "Franca", "14400-000"),
                List.of(new OrderItem("X-Burger", 2, new BigDecimal("25.90")),
                        new OrderItem("Refrigerante", 1, new BigDecimal("7.5"))),
                MICA, T0);
    }

    @Test
    void pedido_novo_comeca_como_recebido_e_registra_no_historico() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEBIDO);
        assertThat(order.getCreatedAt()).isEqualTo(T0);
        assertThat(order.getCreatedBy()).isEqualTo(1L);
        assertThat(order.getStatusHistory())
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.getStatus()).isEqualTo(OrderStatus.RECEBIDO);
                    assertThat(change.getChangedBy()).isEqualTo("Mica");
                    assertThat(change.getChangedAt()).isEqualTo(T0);
                });
    }

    @Test
    void total_e_a_soma_de_preco_vezes_quantidade() {
        assertThat(newOrder().total()).isEqualByComparingTo("59.30");
    }

    @Test
    void preco_e_sempre_guardado_com_duas_casas() {
        assertThat(new OrderItem("Água", 1, new BigDecimal("3")).getUnitPrice()).hasToString("3.00");
    }

    @Test
    void mudar_status_atualiza_o_pedido_e_acrescenta_ao_historico() {
        Order order = newOrder();
        Instant t1 = T0.plusSeconds(600);

        order.changeStatus(OrderStatus.EM_PREPARO, JOAO, t1);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EM_PREPARO);
        assertThat(order.getUpdatedAt()).isEqualTo(t1);
        assertThat(order.getStatusHistory())
                .extracting(StatusChange::getStatus, StatusChange::getChangedBy)
                .containsExactly(
                        tuple(OrderStatus.RECEBIDO, "Mica"),
                        tuple(OrderStatus.EM_PREPARO, "João"));
    }

    @Test
    void percorre_o_fluxo_completo_ate_entregue() {
        Order order = newOrder();

        order.changeStatus(OrderStatus.EM_PREPARO, MICA, T0.plusSeconds(60));
        order.changeStatus(OrderStatus.SAIU_PARA_ENTREGA, MICA, T0.plusSeconds(120));
        order.changeStatus(OrderStatus.ENTREGUE, MICA, T0.plusSeconds(180));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ENTREGUE);
        assertThat(order.getStatusHistory()).hasSize(4);
        assertThat(order.allowedNextStatuses()).isEmpty();
    }

    @Test
    void transicao_invalida_lanca_excecao_e_nao_altera_o_pedido() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.ENTREGUE, MICA, T0.plusSeconds(60)))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("\"Recebido\"")
                .hasMessageContaining("\"Entregue\"");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEBIDO);
        assertThat(order.getStatusHistory()).hasSize(1);
        assertThat(order.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void pedido_cancelado_nao_pode_mais_mudar() {
        Order order = newOrder();
        order.changeStatus(OrderStatus.CANCELADO, MICA, T0.plusSeconds(60));

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.EM_PREPARO, MICA, T0.plusSeconds(120)))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("status final");
    }

    @Test
    void lista_os_proximos_status_permitidos() {
        assertThat(newOrder().allowedNextStatuses())
                .containsExactly(OrderStatus.EM_PREPARO, OrderStatus.CANCELADO);
    }

    @Test
    void nao_cria_pedido_sem_itens() {
        assertThatThrownBy(() -> Order.place(
                new Customer("Ana", "16999990000"),
                new DeliveryAddress("Rua", "1", null, "Centro", "Franca", "14400000"),
                List.of(), MICA, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nao_cria_item_com_quantidade_zero_ou_preco_negativo() {
        assertThatThrownBy(() -> new OrderItem("X", 0, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderItem("X", 1, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
