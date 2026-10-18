package dev.micaelaandrade.pedidos.order;

import dev.micaelaandrade.pedidos.auth.CurrentUser;
import dev.micaelaandrade.pedidos.order.OrderDtos.CreateOrderRequest;
import dev.micaelaandrade.pedidos.order.OrderDtos.OrderResponse;
import dev.micaelaandrade.pedidos.order.OrderDtos.UpdateStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Pedidos")
public class OrderController {

    private final OrderService service;
    private final CurrentUser currentUser;

    public OrderController(OrderService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "Cria um pedido", description = "O pedido sempre começa com o status RECEBIDO.")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        OrderResponse created = service.create(request, currentUser.actor(jwt));
        return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "Lista os pedidos", description = "Mais recentes primeiro. Aceita filtro opcional por status.")
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um pedido pelo ID")
    public OrderResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Atualiza o status do pedido",
            description = "Transições permitidas: RECEBIDO → EM_PREPARO → SAIU_PARA_ENTREGA → ENTREGUE. "
                    + "CANCELADO é permitido a partir de RECEBIDO ou EM_PREPARO. Transição inválida retorna 409. "
                    + "Envie expectedVersion (o campo version do pedido) para receber 409 se o pedido "
                    + "tiver mudado desde que você o consultou.")
    // Exemplo explícito no Swagger: sem ele, o exemplo gerado vinha com "expectedVersion": 0,
    // e quem o usasse como está receberia 409 em qualquer pedido já alterado.
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
            @ExampleObject(name = "Só o novo status", value = "{\"status\": \"EM_PREPARO\"}"),
            @ExampleObject(name = "Com a versão vista na tela",
                    value = "{\"status\": \"EM_PREPARO\", \"expectedVersion\": 0}")}))
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return service.changeStatus(id, request.status(), request.expectedVersion(), currentUser.actor(jwt));
    }
}
