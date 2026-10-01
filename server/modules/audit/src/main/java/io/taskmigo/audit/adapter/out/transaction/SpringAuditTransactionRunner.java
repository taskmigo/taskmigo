package io.taskmigo.audit.adapter.out.transaction;

import io.taskmigo.audit.application.port.out.AuditTransactionRunner;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/// Adapts audit application transaction scopes to Spring transaction management.
@Component
@ConditionalOnProperty(prefix = "taskmigo.audit", name = "enabled", havingValue = "true")
final class SpringAuditTransactionRunner implements AuditTransactionRunner {

    private final TransactionTemplate reads;
    private final TransactionTemplate writes;

    SpringAuditTransactionRunner(PlatformTransactionManager manager) {
        this.reads = new TransactionTemplate(manager);
        this.reads.setReadOnly(true);
        this.writes = new TransactionTemplate(manager);
        this.writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
    }

    @Override
    public <T> T read(Supplier<T> work) {
        return Objects.requireNonNull(this.reads.execute(status -> work.get()));
    }

    @Override
    public void write(Runnable work) {
        this.writes.executeWithoutResult(status -> work.run());
    }
}
