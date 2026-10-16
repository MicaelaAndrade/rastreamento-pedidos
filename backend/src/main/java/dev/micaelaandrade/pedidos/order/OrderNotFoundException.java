package dev.micaelaandrade.pedidos.order;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("Pedido #%d não encontrado.".formatted(id));
    }
}
