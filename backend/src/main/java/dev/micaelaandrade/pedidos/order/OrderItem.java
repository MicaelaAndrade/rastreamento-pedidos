package dev.micaelaandrade.pedidos.order;

import dev.micaelaandrade.pedidos.shared.MoneyConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int quantity;

    /** Guardado em centavos no banco (ver {@link MoneyConverter}). */
    @Convert(converter = MoneyConverter.class)
    @Column(name = "unit_price_cents", nullable = false)
    private BigDecimal unitPrice;

    protected OrderItem() {
        // exigido pelo JPA
    }

    public OrderItem(String name, int quantity, BigDecimal unitPrice) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade precisa ser maior que zero.");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("O preço precisa ser maior que zero.");
        }
        if (unitPrice.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("O preço deve ter no máximo 2 casas decimais.");
        }
        this.name = name;
        this.quantity = quantity;
        // Sempre 2 casas (7.5 vira 7.50). Não perde nada: mais de 2 casas foi recusado acima.
        this.unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
