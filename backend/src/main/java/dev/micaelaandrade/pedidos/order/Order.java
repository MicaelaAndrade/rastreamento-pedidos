package dev.micaelaandrade.pedidos.order;

import dev.micaelaandrade.pedidos.shared.InstantConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Pedido de delivery. As regras de negócio ficam aqui dentro (e em {@link OrderStatus}),
 * não no service: a única forma de mudar o status é pelo método {@link #changeStatus},
 * que valida a transição e registra o histórico.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Embedded
    private Customer customer;

    @Embedded
    private DeliveryAddress deliveryAddress;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OrderBy("id")
    private List<StatusChange> statusHistory = new ArrayList<>();

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Convert(converter = InstantConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Convert(converter = InstantConverter.class)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Controle de concorrência otimista: duas pessoas mudando o mesmo pedido ao mesmo tempo não se sobrescrevem. */
    @Version
    private Long version;

    protected Order() {
        // exigido pelo JPA
    }

    /** Cria um pedido novo, que sempre começa como {@link OrderStatus#RECEBIDO}. */
    public static Order place(Customer customer, DeliveryAddress deliveryAddress, List<OrderItem> items,
                              Actor placedBy, Instant now) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("O pedido precisa ter pelo menos um item.");
        }
        Order order = new Order();
        order.customer = customer;
        order.deliveryAddress = deliveryAddress;
        order.items.addAll(items);
        order.createdBy = placedBy.id();
        order.createdAt = now;
        order.moveTo(OrderStatus.RECEBIDO, placedBy, now);
        return order;
    }

    public void changeStatus(OrderStatus next, Actor changedBy, Instant now) {
        if (!status.canChangeTo(next)) {
            throw new InvalidStatusTransitionException(id, status, next);
        }
        moveTo(next, changedBy, now);
    }

    private void moveTo(OrderStatus next, Actor actor, Instant now) {
        this.status = next;
        this.updatedAt = now;
        this.statusHistory.add(new StatusChange(next, now, actor.name()));
    }

    public BigDecimal total() {
        return items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Set<OrderStatus> allowedNextStatuses() {
        return status.nextStatuses();
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Customer getCustomer() {
        return customer;
    }

    public DeliveryAddress getDeliveryAddress() {
        return deliveryAddress;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<StatusChange> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Quem está agindo sobre o pedido (o usuário logado). */
    public record Actor(Long id, String name) {
    }
}
