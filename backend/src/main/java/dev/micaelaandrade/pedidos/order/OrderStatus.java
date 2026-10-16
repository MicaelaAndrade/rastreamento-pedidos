package dev.micaelaandrade.pedidos.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * Status de um pedido e as regras de transição entre eles.
 *
 * <pre>
 * RECEBIDO → EM_PREPARO → SAIU_PARA_ENTREGA → ENTREGUE
 *     ↓           ↓
 * CANCELADO   CANCELADO
 * </pre>
 *
 * Um pedido pode ser cancelado até sair para entrega. ENTREGUE e CANCELADO são finais.
 */
public enum OrderStatus {

    RECEBIDO("Recebido"),
    EM_PREPARO("Em preparo"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    ENTREGUE("Entregue"),
    CANCELADO("Cancelado");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Para quais status o pedido pode ir a partir deste.
     * O switch é exaustivo: se um status novo for criado, o código só compila
     * depois que as transições dele forem definidas aqui.
     */
    public Set<OrderStatus> nextStatuses() {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARO, CANCELADO);
            case EM_PREPARO -> EnumSet.of(SAIU_PARA_ENTREGA, CANCELADO);
            case SAIU_PARA_ENTREGA -> EnumSet.of(ENTREGUE);
            case ENTREGUE, CANCELADO -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canChangeTo(OrderStatus next) {
        return nextStatuses().contains(next);
    }

    public boolean isFinal() {
        return nextStatuses().isEmpty();
    }
}
