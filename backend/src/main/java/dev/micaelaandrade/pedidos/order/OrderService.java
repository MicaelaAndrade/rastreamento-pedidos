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
                new Customer(request.customer().name().trim(), request.customer().phone().trim()),
                new DeliveryAddress(a.street().trim(), a.number().trim(), blankToNull(a.complement()),
                        a.neighborhood().trim(), a.city().trim(), a.zipCode().trim()),
                request.items().stream()
                        .map(i -> new OrderItem(i.name().trim(), i.quantity(), i.unitPrice()))
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

    public OrderResponse changeStatus(Long id, OrderStatus next, Actor actor) {
        Order order = find(id);
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
        return value == null || value.isBlank() ? null : value.trim();
    }
}
