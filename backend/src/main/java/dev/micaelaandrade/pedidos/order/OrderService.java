package dev.micaelaandrade.pedidos.order;

import dev.micaelaandrade.pedidos.order.Order.Actor;
import dev.micaelaandrade.pedidos.order.OrderDtos.AddressRequest;
import dev.micaelaandrade.pedidos.order.OrderDtos.CreateOrderRequest;
import dev.micaelaandrade.pedidos.order.OrderDtos.OrderResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de pedidos. O service coordena (busca, salva, converte para resposta);
 * as regras de negócio ficam na entidade {@link Order}.
 */
@Service
@Transactional
public class OrderService {

    private final OrderRepository orders;
    private final Clock clock;

    public OrderService(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    public OrderResponse create(CreateOrderRequest request, Actor actor) {
        AddressRequest a = request.deliveryAddress();
        Order order = Order.place(
                new Customer(request.customer().name(), request.customer().phone()),
                new DeliveryAddress(a.street(), a.number(), blankToNull(a.complement()),
                        a.neighborhood(), a.city(), a.zipCode()),
                request.items().stream()
                        .map(i -> new OrderItem(i.name(), i.quantity(), i.unitPrice()))
                        .toList(),
                actor,
                now());
        return OrderResponse.from(orders.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(OrderStatus status) {
        List<Order> found = status == null
                ? orders.findAllByOrderByIdDesc()
                : orders.findAllByStatusOrderByIdDesc(status);
        return found.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return OrderResponse.from(find(id));
    }

    /**
     * @param expectedVersion versão que a tela mostrava (opcional). Se o pedido mudou desde então,
     *                        a mudança é recusada com 409, para ninguém agir sobre um estado que não viu.
     */
    public OrderResponse changeStatus(Long id, OrderStatus next, Long expectedVersion, Actor actor) {
        Order order = find(id);
        if (expectedVersion != null && !expectedVersion.equals(order.getVersion())) {
            throw new OrderChangedException(id, order.getStatus());
        }
        order.changeStatus(next, actor, now());
        // saveAndFlush força o UPDATE aqui dentro, para um conflito de versão
        // virar erro 409 nesta requisição em vez de estourar depois do retorno.
        return OrderResponse.from(orders.saveAndFlush(order));
    }

    private Order find(Long id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private Instant now() {
        // O banco guarda milissegundos; truncar aqui deixa a resposta igual ao que for lido depois.
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
