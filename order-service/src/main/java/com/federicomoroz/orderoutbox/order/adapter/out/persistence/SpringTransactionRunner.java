package com.federicomoroz.orderoutbox.order.adapter.out.persistence;

import com.federicomoroz.orderoutbox.order.application.port.out.TransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * The single implementation of {@link TransactionRunner}. Wraps {@link TransactionTemplate}
 * instead of using {@code @Transactional} so that no framework annotation ever has to appear on
 * {@code application/service} code.
 */
@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    @Override
    public <T> T run(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
