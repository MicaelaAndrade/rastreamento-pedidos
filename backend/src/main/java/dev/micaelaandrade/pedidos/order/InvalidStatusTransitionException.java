package dev.micaelaandrade.pedidos.order;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(Long orderId, OrderStatus current, OrderStatus requested) {
        super(buildMessage(orderId, current, requested));
    }

    private static String buildMessage(Long orderId, OrderStatus current, OrderStatus requested) {
        String prefix = "O pedido #%d está \"%s\"".formatted(orderId, current.label());
        if (current == requested) {
            return prefix + " e já se encontra nesse status.";
        }
        if (current.isFinal()) {
            return prefix + ", que é um status final, e não pode mais mudar.";
        }
        return prefix + " e não pode ir para \"%s\".".formatted(requested.label());
    }
}
