package com.federicomoroz.orderoutbox.order.adapter.in.web;

import com.federicomoroz.orderoutbox.order.application.port.in.CreateOrderCommand;
import com.federicomoroz.orderoutbox.order.application.port.in.CreateOrderUseCase;
import com.federicomoroz.orderoutbox.order.application.port.in.GetOrderUseCase;
import com.federicomoroz.orderoutbox.order.application.port.out.OrderQueryPort;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.domain.OrderId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Write path goes through inbound ports ({@link CreateOrderUseCase}, {@link GetOrderUseCase});
 * the newest-first listing reads straight through the {@link OrderQueryPort} outbound port —
 * the same CQRS-lite shortcut {@code NotificationController} established for reads with no
 * business logic. Mixing both in one controller is intentional: the resource is the same, only
 * the depth of the path through the hexagon differs.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** Bounded on purpose: this feeds a dashboard polling once per second, not a data export. */
    static final int RECENT_ORDERS_LIMIT = 50;

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final OrderQueryPort orderQueryPort;

    public OrderController(CreateOrderUseCase createOrderUseCase, GetOrderUseCase getOrderUseCase,
                            OrderQueryPort orderQueryPort) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.orderQueryPort = orderQueryPort;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = createOrderUseCase.createOrder(new CreateOrderCommand(
                request.customerId(),
                request.productId(),
                request.quantity(),
                request.unitPriceAmount(),
                request.unitPriceCurrency()));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.id().value())
                .toUri();

        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    @GetMapping
    public List<OrderResponse> listRecentOrders() {
        return orderQueryPort.findRecent(RECENT_ORDERS_LIMIT)
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return getOrderUseCase.getOrder(OrderId.of(id))
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
