package dev.micaelaandrade.pedidos.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "RECEBIDO,          EM_PREPARO",
            "RECEBIDO,          CANCELADO",
            "EM_PREPARO,        SAIU_PARA_ENTREGA",
            "EM_PREPARO,        CANCELADO",
            "SAIU_PARA_ENTREGA, ENTREGUE",
    })
    void permite_as_transicoes_do_fluxo(OrderStatus from, OrderStatus to) {
        assertThat(from.canChangeTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} ↛ {1}")
    @CsvSource({
            // pular etapas
            "RECEBIDO,          SAIU_PARA_ENTREGA",
            "RECEBIDO,          ENTREGUE",
            "EM_PREPARO,        ENTREGUE",
            // voltar etapas
            "EM_PREPARO,        RECEBIDO",
            "SAIU_PARA_ENTREGA, EM_PREPARO",
            // cancelar depois que o pedido saiu
            "SAIU_PARA_ENTREGA, CANCELADO",
            // sair de um status final
            "ENTREGUE,          CANCELADO",
            "ENTREGUE,          RECEBIDO",
            "CANCELADO,         RECEBIDO",
            "CANCELADO,         EM_PREPARO",
    })
    void recusa_transicoes_fora_do_fluxo(OrderStatus from, OrderStatus to) {
        assertThat(from.canChangeTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void nenhum_status_vai_para_ele_mesmo(OrderStatus status) {
        assertThat(status.canChangeTo(status)).isFalse();
    }

    @Test
    void apenas_entregue_e_cancelado_sao_finais() {
        assertThat(OrderStatus.ENTREGUE.isFinal()).isTrue();
        assertThat(OrderStatus.CANCELADO.isFinal()).isTrue();
        assertThat(OrderStatus.RECEBIDO.isFinal()).isFalse();
        assertThat(OrderStatus.EM_PREPARO.isFinal()).isFalse();
        assertThat(OrderStatus.SAIU_PARA_ENTREGA.isFinal()).isFalse();
    }
}
