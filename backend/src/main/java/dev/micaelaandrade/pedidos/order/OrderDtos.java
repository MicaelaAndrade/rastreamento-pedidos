package dev.micaelaandrade.pedidos.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Formatos de entrada e saída da API de pedidos. */
public final class OrderDtos {

    private OrderDtos() {
    }

    // ---------- Entrada ----------

    public record CreateOrderRequest(
            @NotNull(message = "Informe os dados do cliente.") @Valid CustomerRequest customer,
            @NotNull(message = "Informe o endereço de entrega.") @Valid AddressRequest deliveryAddress,
            @NotEmpty(message = "O pedido precisa ter pelo menos um item.")
            @Size(max = 50, message = "O pedido pode ter no máximo 50 itens.")
            List<@NotNull @Valid ItemRequest> items) {
    }

    public record CustomerRequest(
            @NotBlank(message = "Informe o nome do cliente.")
            @Size(max = 120, message = "O nome do cliente pode ter no máximo 120 caracteres.")
            String name,
            @NotBlank(message = "Informe o telefone do cliente.")
            @Pattern(regexp = "^[0-9()+\\-\\s]{8,20}$",
                    message = "Telefone inválido. Use só números, espaços, parênteses, + e -.")
            String phone) {
    }

    public record AddressRequest(
            @NotBlank(message = "Informe a rua.") @Size(max = 200) String street,
            @NotBlank(message = "Informe o número.") @Size(max = 20) String number,
            @Size(max = 100, message = "O complemento pode ter no máximo 100 caracteres.") String complement,
            @NotBlank(message = "Informe o bairro.") @Size(max = 100) String neighborhood,
            @NotBlank(message = "Informe a cidade.") @Size(max = 100) String city,
            @NotBlank(message = "Informe o CEP.")
            @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 00000-000.")
            String zipCode) {
    }

    public record ItemRequest(
            @NotBlank(message = "Informe o nome de cada item.") @Size(max = 120) String name,
            @NotNull(message = "Informe a quantidade de cada item.")
            @Min(value = 1, message = "A quantidade mínima de um item é 1.")
            @Max(value = 999, message = "A quantidade máxima de um item é 999.")
            Integer quantity,
            @NotNull(message = "Informe o preço de cada item.")
            @PositiveOrZero(message = "O preço de um item não pode ser negativo.")
            @Digits(integer = 7, fraction = 2, message = "O preço deve ter no máximo 2 casas decimais.")
            BigDecimal unitPrice) {
    }

    public record UpdateStatusRequest(
            @NotNull(message = "Informe o novo status.") OrderStatus status) {
    }

    // ---------- Saída ----------

    public record OrderResponse(
            Long id,
            OrderStatus status,
            CustomerResponse customer,
            AddressResponse deliveryAddress,
            List<ItemResponse> items,
            BigDecimal total,
            Instant createdAt,
            Instant updatedAt,
            List<StatusChangeResponse> statusHistory,
            List<OrderStatus> allowedNextStatuses) {

        public static OrderResponse from(Order order) {
            Customer c = order.getCustomer();
            DeliveryAddress a = order.getDeliveryAddress();
            return new OrderResponse(
                    order.getId(),
                    order.getStatus(),
                    new CustomerResponse(c.getName(), c.getPhone()),
                    new AddressResponse(a.getStreet(), a.getNumber(), a.getComplement(), a.getNeighborhood(),
                            a.getCity(), a.getZipCode()),
                    order.getItems().stream()
                            .map(i -> new ItemResponse(i.getName(), i.getQuantity(), i.getUnitPrice(), i.subtotal()))
                            .toList(),
                    order.total(),
                    order.getCreatedAt(),
                    order.getUpdatedAt(),
                    order.getStatusHistory().stream()
                            .map(s -> new StatusChangeResponse(s.getStatus(), s.getChangedAt(), s.getChangedBy()))
                            .toList(),
                    List.copyOf(order.allowedNextStatuses()));
        }
    }

    public record CustomerResponse(String name, String phone) {
    }

    public record AddressResponse(String street, String number, String complement, String neighborhood,
                                  String city, String zipCode) {
    }

    public record ItemResponse(String name, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }

    public record StatusChangeResponse(OrderStatus status, Instant changedAt, String changedBy) {
    }
}
