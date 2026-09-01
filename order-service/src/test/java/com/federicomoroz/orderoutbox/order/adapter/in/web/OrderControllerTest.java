package com.federicomoroz.orderoutbox.order.adapter.in.web;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.federicomoroz.orderoutbox.order.application.service.CreateOrderService;
import com.federicomoroz.orderoutbox.order.application.service.GetOrderService;
import com.federicomoroz.orderoutbox.order.domain.CustomerId;
import com.federicomoroz.orderoutbox.order.domain.Money;
import com.federicomoroz.orderoutbox.order.domain.Order;
import com.federicomoroz.orderoutbox.order.testsupport.FakeEventSerializer;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryOrderRepository;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryOutboxRepository;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryTransactionRunner;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pure unit test of the HTTP surface: a standalone {@code MockMvc} around the real controller,
 * wired to the same hand-written in-memory fakes the application-service tests use — no Spring
 * context, no Mockito, no database.
 *
 * <p>Worth going through MockMvc rather than calling the method directly, because the thing
 * most likely to break is the routing itself: {@code GET /api/orders} (the collection) has to
 * coexist with {@code GET /api/orders/{id}} (a single order) without either shadowing the other.
 */
class OrderControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new OrderController(
                    new CreateOrderService(orders, outbox, new FakeEventSerializer(),
                            new InMemoryTransactionRunner(), FIXED_CLOCK),
                    new GetOrderService(orders),
                    orders))
            // Standalone MockMvc has no Spring Boot auto-configuration, so Jackson is configured
            // here to match what Boot produces at runtime: ISO-8601 instants, not epoch numbers.
            .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build()))
            .build();

    @Test
    void listsRecentOrdersNewestFirst() throws Exception {
        orders.save(orderPlacedAt("sku-older", "2026-01-01T10:00:00Z"));
        orders.save(orderPlacedAt("sku-newer", "2026-01-01T10:00:05Z"));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].productId").value("sku-newer"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-01-01T10:00:05Z"))
                .andExpect(jsonPath("$[1].productId").value("sku-older"));
    }

    @Test
    void listingIsCappedByTheNamedLimit_neverAnUnboundedDump() throws Exception {
        for (int i = 0; i < OrderController.RECENT_ORDERS_LIMIT + 7; i++) {
            orders.save(orderPlacedAt("sku-" + i, "2026-01-01T10:00:00Z"));
        }

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(OrderController.RECENT_ORDERS_LIMIT));
    }

    @Test
    void theCollectionRouteDoesNotShadowTheSingleOrderLookup() throws Exception {
        Order order = orderPlacedAt("sku-single", "2026-01-01T10:00:00Z");
        orders.save(order);

        mockMvc.perform(get("/api/orders/{id}", order.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("sku-single"));

        mockMvc.perform(get("/api/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmptyOrdersTableIsAnEmptyArray_notAnError() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private static Order orderPlacedAt(String productId, String createdAt) {
        return Order.place(CustomerId.of(UUID.randomUUID()), productId, 1,
                Money.of(new BigDecimal("19.99"), "USD"),
                Clock.fixed(Instant.parse(createdAt), ZoneOffset.UTC));
    }
}
