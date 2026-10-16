package dev.micaelaandrade.pedidos.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class DeliveryAddress {

    @Column(name = "address_street", nullable = false)
    private String street;

    @Column(name = "address_number", nullable = false)
    private String number;

    @Column(name = "address_complement")
    private String complement;

    @Column(name = "address_neighborhood", nullable = false)
    private String neighborhood;

    @Column(name = "address_city", nullable = false)
    private String city;

    @Column(name = "address_zip_code", nullable = false)
    private String zipCode;

    protected DeliveryAddress() {
        // exigido pelo JPA
    }

    public DeliveryAddress(String street, String number, String complement, String neighborhood,
                           String city, String zipCode) {
        this.street = street;
        this.number = number;
        this.complement = complement;
        this.neighborhood = neighborhood;
        this.city = city;
        this.zipCode = zipCode;
    }

    public String getStreet() {
        return street;
    }

    public String getNumber() {
        return number;
    }

    public String getComplement() {
        return complement;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public String getCity() {
        return city;
    }

    public String getZipCode() {
        return zipCode;
    }
}
