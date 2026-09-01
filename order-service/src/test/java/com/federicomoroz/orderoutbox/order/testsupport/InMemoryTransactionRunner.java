package com.federicomoroz.orderoutbox.order.testsupport;

import com.federicomoroz.orderoutbox.order.application.port.out.TransactionRunner;

import java.util.function.Supplier;

/**
 * Fake used by pure application-service unit tests. Runs the action synchronously with no real
 * transaction semantics — actual DB atomicity (the property that matters) is proven separately by
 * {@code OrderPersistenceAdapterIT} against a real Postgres via Testcontainers, not by this fake.
 */
public final class InMemoryTransactionRunner implements TransactionRunner {

    @Override
    public void run(Runnable action) {
        action.run();
    }

    @Override
    public <T> T run(Supplier<T> action) {
        return action.get();
    }
}
