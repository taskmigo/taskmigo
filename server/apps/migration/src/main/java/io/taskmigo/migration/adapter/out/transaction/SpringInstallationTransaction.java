package io.taskmigo.migration.adapter.out.transaction;

import io.taskmigo.migration.application.port.out.InstallationTransaction;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/// Adapts the installation transaction port to Spring SERIALIZABLE transactions and retryable data-access failures.
@Component
final class SpringInstallationTransaction implements InstallationTransaction {

    private final TransactionTemplate transactions;

    SpringInstallationTransaction(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    @Override
    public <T> T serializable(int maxAttempts, Supplier<T> work) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return Objects.requireNonNull(this.transactions.execute(status -> work.get()));
            } catch (TransientDataAccessException | DataIntegrityViolationException exception) {
                if (attempt == maxAttempts) {
                    throw exception;
                }
            }
        }
    }
}
