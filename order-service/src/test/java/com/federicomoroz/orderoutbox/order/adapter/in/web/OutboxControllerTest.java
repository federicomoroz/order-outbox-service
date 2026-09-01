package com.federicomoroz.orderoutbox.order.adapter.in.web;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.federicomoroz.orderoutbox.order.domain.OutboxEvent;
import com.federicomoroz.orderoutbox.order.testsupport.InMemoryOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * This is the endpoint that makes the Transactional Outbox guarantee observable from outside the
 * database, so the test is written around the lifecycle it has to expose: a row shows up
 * {@code PENDING} with no {@code publishedAt}, and the very same row later reads back
 * {@code PUBLISHED} with one.
 */
class OutboxControllerTest {

    private final InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new OutboxController(outbox))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                    .addModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build()))
            .build();

    @Test
    void exposesTheStatusTransitionThatMakesTheOutboxVisible() throws Exception {
        OutboxEvent event = eventRecordedAt("2026-01-01T10:00:00Z");
        outbox.save(event);

        mockMvc.perform(get("/api/outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(event.id().value().toString()))
                .andExpect(jsonPath("$[0].eventType").value("OrderCreated"))
                .andExpect(jsonPath("$[0].aggregateId").value(event.aggregateId()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].publishAttempts").value(0))
                .andExpect(jsonPath("$[0].occurredAt").value("2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].publishedAt").isEmpty());

        event.markPublished(Instant.parse("2026-01-01T10:00:02Z"));
        outbox.save(event);

        mockMvc.perform(get("/api/outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].publishedAt").value("2026-01-01T10:00:02Z"));
    }

    @Test
    void reportsFailedEventsWithTheirAttemptCount() throws Exception {
        OutboxEvent event = eventRecordedAt("2026-01-01T10:00:00Z");
        for (int i = 0; i < OutboxEvent.MAX_PUBLISH_ATTEMPTS; i++) {
            event.recordFailedAttempt();
        }
        outbox.save(event);

        mockMvc.perform(get("/api/outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].publishAttempts").value(OutboxEvent.MAX_PUBLISH_ATTEMPTS));
    }

    @Test
    void listsNewestFirst() throws Exception {
        outbox.save(eventRecordedAt("2026-01-01T10:00:00Z"));
        OutboxEvent newest = eventRecordedAt("2026-01-01T10:00:09Z");
        outbox.save(newest);

        mockMvc.perform(get("/api/outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newest.id().value().toString()));
    }

    @Test
    void listingIsCappedByTheNamedLimit_neverAnUnboundedDump() throws Exception {
        for (int i = 0; i < OutboxController.RECENT_OUTBOX_EVENTS_LIMIT + 7; i++) {
            outbox.save(eventRecordedAt("2026-01-01T10:00:00Z"));
        }

        mockMvc.perform(get("/api/outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(OutboxController.RECENT_OUTBOX_EVENTS_LIMIT));
    }

    @Test
    void anEmptyOutboxIsAnEmptyArray_notAnError() throws Exception {
        mockMvc.perform(get("/api/outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private static OutboxEvent eventRecordedAt(String occurredAt) {
        return OutboxEvent.record("Order", "order-" + UUID.randomUUID(), "OrderCreated",
                "{\"k\":\"v\"}", Instant.parse(occurredAt));
    }
}
