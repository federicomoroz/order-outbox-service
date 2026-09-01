package com.federicomoroz.orderoutbox.notification.testsupport;

import com.federicomoroz.orderoutbox.notification.application.port.out.TransactionRunner;

import java.util.function.Supplier;

/** Fake used by pure application-service unit tests. Runs synchronously with no real rollback
 * semantics — real atomicity is proven separately, against real Postgres, by the Testcontainers
 * integration tests. */
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
