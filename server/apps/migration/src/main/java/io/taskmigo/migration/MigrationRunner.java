package io.taskmigo.migration;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/// Runs the complete installation-state reconciliation as one serializable unit.
@Component
final class MigrationRunner implements ApplicationRunner {

    private static final int MAX_RECONCILIATION_ATTEMPTS = 3;

    private final MigrationResourceLoader resources;
    private final ManagedResourceReconciler resourcesReconciler;
    private final InternalClientReconciler clients;
    private final MigrationChangeLogger changeLogger;
    private final TransactionTemplate transactions;

    MigrationRunner(
        MigrationResourceLoader resources,
        ManagedResourceReconciler resourcesReconciler,
        InternalClientReconciler clients,
        MigrationChangeLogger changeLogger,
        PlatformTransactionManager transactionManager
    ) {
        this.resources = resources;
        this.resourcesReconciler = resourcesReconciler;
        this.clients = clients;
        this.changeLogger = changeLogger;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    @Override
    public void run(ApplicationArguments arguments) {
        this.migrate();
    }

    void migrate() {
        this.reconcile(this.resources.load());
    }

    void reconcile(MigrationResourceLoader.MigrationResources data) {
        this.resourcesReconciler.validate(data);
        this.clients.validate(data.clients());

        for (int attempt = 1; ; attempt++) {
            List<MigrationChange> changes = new ArrayList<>();
            try {
                this.transactions.executeWithoutResult(status -> {
                    this.resourcesReconciler.reconcile(data, changes);
                    this.clients.reconcile(data.clients(), changes);
                });
                this.changeLogger.log(changes);
                return;
            } catch (TransientDataAccessException | DataIntegrityViolationException exception) {
                if (attempt == MAX_RECONCILIATION_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }
}
