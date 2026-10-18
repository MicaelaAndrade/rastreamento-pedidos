package dev.micaelaandrade.pedidos.order;

import dev.micaelaandrade.pedidos.shared.validation.Phone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Formatos de entrada e saída da API de pedidos. */
public final class OrderDtos {

    private OrderDtos() {
    }

    // ---------- Entrada ----------
    // Os textos chegam sem espaços nas pontas (os construtores compactos fazem o strip),
    // então a validação e o tamanho máximo valem para o texto que de fato será salvo.

    public record CreateOrderRequest(
            @NotNull(message = "Informe os dados do cliente.") @Valid CustomerRequest customer,
            @NotNull(message = "Informe o endereço de entrega.") @Valid AddressRequest deliveryAddress,
            @NotEmpty(message = "O pedido precisa ter pelo menos um item.")
            @Size(max = 50, message = "O pedido pode ter no máximo 50 itens.")
            List<@NotNull(message = "Item vazio na lista de itens.") @Valid ItemRequest> items) {
    }

    public record CustomerRequest(
            @NotBlank(message = "Informe o nome do cliente.")
            @Size(max = 120, message = "O nome do cliente pode ter no máximo 120 caracteres.")
            String name,
            @NotBlank(message = "Informe o telefone do cliente.")
            @Size(max = 20, message = "O telefone pode ter no máximo 20 caracteres.")
            @Phone
            String phone) {

        public CustomerRequest {
            name = strip(name);
            phone = strip(phone);
        }
    }

    public record AddressRequest(
            @NotBlank(message = "Informe a rua.")
            @Size(max = 200, message = "A rua pode ter no máximo 200 caracteres.")
            String street,
            @NotBlank(message = "Informe o número.")
            @Size(max = 20, message = "O número pode ter no máximo 20 caracteres.")
            String number,
            @Size(max = 100, message = "O complemento pode ter no máximo 100 caracteres.")
            String complement,
            @NotBlank(message = "Informe o bairro.")
            @Size(max = 100, message = "O bairro pode ter no máximo 100 caracteres.")
            String neighborhood,
            @NotBlank(message = "Informe a cidade.")
            @Size(max = 100, message = "A cidade pode ter no máximo 100 caracteres.")
            String city,
            @NotBlank(message = "Informe o CEP.")
            @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 00000-000.")
            String zipCode) {

        public AddressRequest {
            street = strip(street);
            number = strip(number);
            complement = strip(complement);
            neighborhood = strip(neighborhood);
            city = strip(city);
            zipCode = strip(zipCode);
        }
    }

    public record ItemRequest(
            @NotBlank(message = "Informe o nome de cada item.")
            @Size(max = 120, message = "O nome de um item pode ter no máximo 120 caracteres.")
            String name,
            @NotNull(message = "Informe a quantidade de cada item.")
            @Min(value = 1, message = "A quantidade mínima de um item é 1.")
            @Max(value = 999, message = "A quantidade máxima de um item é 999.")
            Integer quantity,
            @NotNull(message = "Informe o preço de cada item.")
            @Positive(message = "O preço de um item precisa ser maior que zero.")
            @DecimalMax(value = "9999999.99", message = "O preço máximo de um item é R$ 9.999.999,99.")
            // integer alto de propósito: o limite de valor é do @DecimalMax; aqui só as casas decimais.
            @Digits(integer = 100, fraction = 2, message = "O preço deve ter no máximo 2 casas decimais.")
            BigDecimal unitPrice) {

        public ItemRequest {
            name = strip(name);
        }
    }

    /**
     * @param status          novo status
     * @param expectedVersion opcional: a versão do pedido que a tela estava mostrando. Se o pedido
     *                        mudou desde então (outra pessoa avançou ou cancelou), a API responde 409
     *                        em vez de aplicar a mudança sobre um estado que a pessoa não viu.
     */
    public record UpdateStatusRequest(
            @NotNull(message = "Informe o novo status.") OrderStatus status,
            Long expectedVersion) {
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
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
            List<OrderStatus> allowedNextStatuses,
            /* Muda a cada alteração do pedido; envie em expectedVersion ao mudar o status. */
            Long version) {

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
                    List.copyOf(order.allowedNextStatuses()),
                    order.getVersion());
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
