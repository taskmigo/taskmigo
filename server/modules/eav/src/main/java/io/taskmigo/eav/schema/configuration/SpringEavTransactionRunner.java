package io.taskmigo.eav.schema.configuration;

import io.taskmigo.eav.schema.application.EavTransactionRunner;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
final class SpringEavTransactionRunner implements EavTransactionRunner {

    private final TransactionTemplate reads;
    private final TransactionTemplate writes;

    SpringEavTransactionRunner(PlatformTransactionManager manager) {
        this.reads = new TransactionTemplate(manager);
        this.reads.setReadOnly(true);
        this.writes = new TransactionTemplate(manager);
    }

    @Override
    public <T> T read(Supplier<T> work) {
        return Objects.requireNonNull(this.reads.execute(status -> work.get()));
    }

    @Override
    public <T> T write(Supplier<T> work) {
        return Objects.requireNonNull(this.writes.execute(status -> work.get()));
    }

    @Override
    public void write(Runnable work) {
        this.writes.executeWithoutResult(status -> work.run());
    }
}
