package dev.micaelaandrade.pedidos.order;

/** O pedido mudou desde que a pessoa o viu: a mudança pedida foi feita sobre um estado desatualizado. */
public class OrderChangedException extends RuntimeException {

    public OrderChangedException(Long id, OrderStatus currentStatus) {
        super("O pedido #%d foi alterado por outra pessoa e agora está \"%s\". Confira e tente de novo."
                .formatted(id, currentStatus.label()));
    }
}
