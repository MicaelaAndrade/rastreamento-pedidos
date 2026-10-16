package dev.micaelaandrade.pedidos.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Quem recebe o pedido (não confundir com o usuário do sistema que o cadastrou). */
@Embeddable
public class Customer {

    @Column(name = "customer_name", nullable = false)
    private String name;

    @Column(name = "customer_phone", nullable = false)
    private String phone;

    protected Customer() {
        // exigido pelo JPA
    }

    public Customer(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}
