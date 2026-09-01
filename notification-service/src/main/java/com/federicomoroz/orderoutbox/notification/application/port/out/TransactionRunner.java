package com.federicomoroz.orderoutbox.notification.application.port.out;

import java.util.function.Supplier;

/**
 * Secondary port that runs an action inside a single database transaction — this module's own
 * copy of the same port defined in {@code order-service}, for the same reason: it keeps
 * {@code @Transactional}/{@code TransactionTemplate} out of {@code application/service}.
 *
 * <p>In {@code HandleOrderCreatedEventService}, marking the event processed and saving the
 * resulting {@code Notification} run inside one call to {@link #run(Supplier)}: if the
 * notification write fails, the whole transaction rolls back — including the idempotency
 * marker — so a retried delivery is not incorrectly treated as a duplicate.
 */
public interface TransactionRunner {

    void run(Runnable action);

    <T> T run(Supplier<T> action);
}
