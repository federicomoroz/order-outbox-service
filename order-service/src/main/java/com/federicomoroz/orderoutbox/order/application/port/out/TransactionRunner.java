package com.federicomoroz.orderoutbox.order.application.port.out;

import java.util.function.Supplier;

/**
 * Secondary port that runs an action inside a single database transaction.
 *
 * <p>Exists because {@code Order} and {@code OutboxEvent} are written through two separate
 * secondary ports ({@code OrderRepository}, {@code OutboxRepository}) but must land in exactly
 * one transaction — the entire point of the Transactional Outbox pattern. Without this port,
 * {@code @Transactional} would have to live on {@code application/service}, which would import
 * {@code org.springframework.transaction} and break the domain/application purity that ArchUnit
 * enforces. The single implementation, {@code SpringTransactionRunner}, wraps
 * {@code TransactionTemplate}.
 */
public interface TransactionRunner {

    void run(Runnable action);

    <T> T run(Supplier<T> action);
}
