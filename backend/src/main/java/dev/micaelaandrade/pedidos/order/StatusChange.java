package dev.micaelaandrade.pedidos.order;

import dev.micaelaandrade.pedidos.shared.InstantConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Uma linha do histórico: o pedido entrou no status X, em tal momento, por ação de tal pessoa. */
@Entity
@Table(name = "order_status_history")
public class StatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Convert(converter = InstantConverter.class)
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    /** Nome de quem fez a mudança, guardado como estava no momento (não muda se o usuário mudar de nome). */
    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    protected StatusChange() {
        // exigido pelo JPA
    }

    StatusChange(OrderStatus status, Instant changedAt, String changedBy) {
        this.status = status;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }
}
