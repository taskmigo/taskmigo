package io.taskmigo.migration.application.service;

import io.taskmigo.authorization.provisioning.application.port.in.api.AuthorizationProvisioningService;
import io.taskmigo.identity.provisioning.application.port.in.api.GroupProvisioningService;
import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.migration.application.model.InstallationPlan;
import io.taskmigo.migration.infrastructure.oauth.ManagedClientRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/// Runs the complete one-shot installation reconciliation.
@Component
public final class MigrationInstaller {

    private static final int MAX_RECONCILIATION_ATTEMPTS = 3;

    private final ManagedResourceReconciler resources;
    private final InternalClientReconciler clients;
    private final TransactionTemplate transactions;

    public MigrationInstaller(
        AuthorizationProvisioningService authorization,
        IdentityProvisioningService identity,
        GroupProvisioningService groups,
        ManagedClientRepository clients,
        PasswordEncoder passwords,
        PlatformTransactionManager transactionManager
    ) {
        this.resources = new ManagedResourceReconciler(authorization, identity, groups, passwords);
        this.clients = new InternalClientReconciler(clients, passwords);
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    public void install(InstallationPlan plan) {
        this.resources.validate(plan);
        this.clients.validate(plan.clients());
        this.reconcile(plan);
    }

    private void reconcile(InstallationPlan plan) {
        for (int attempt = 1; ; attempt++) {
            try {
                this.transactions.executeWithoutResult(status -> {
                    this.resources.reconcile(plan);
                    this.clients.reconcile(plan.clients());
                });
                return;
            } catch (TransientDataAccessException | DataIntegrityViolationException exception) {
                if (attempt == MAX_RECONCILIATION_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }
}
