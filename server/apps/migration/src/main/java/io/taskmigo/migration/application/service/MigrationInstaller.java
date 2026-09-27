package io.taskmigo.migration.application.service;

import io.taskmigo.authorization.provisioning.application.port.in.api.AuthorizationProvisioningService;
import io.taskmigo.identity.provisioning.application.port.in.api.GroupProvisioningService;
import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.migration.application.model.InstallationChange;
import io.taskmigo.migration.application.model.InstallationPlan;
import io.taskmigo.migration.infrastructure.oauth.ManagedClientRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationInstaller.class);

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
        List<InstallationChange> changes = this.reconcile(plan);
        this.publish(changes);
    }

    private List<InstallationChange> reconcile(InstallationPlan plan) {
        for (int attempt = 1; ; attempt++) {
            try {
                return Objects.requireNonNull(
                    this.transactions.execute(status -> {
                        List<InstallationChange> changes = new ArrayList<>();
                        this.resources.reconcile(plan, changes);
                        this.clients.reconcile(plan.clients(), changes);
                        return List.copyOf(changes);
                    })
                );
            } catch (TransientDataAccessException | DataIntegrityViolationException exception) {
                if (attempt == MAX_RECONCILIATION_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }

    private void publish(List<InstallationChange> changes) {
        for (InstallationChange change : changes) {
            if (change.action() == InstallationChange.Action.UNCHANGED) {
                continue;
            }
            LOGGER.atInfo()
                .addKeyValue("event.type", "change")
                .addKeyValue("event.action", change.action().value())
                .addKeyValue("taskmigo.migration.resource.type", change.resourceType())
                .addKeyValue("taskmigo.migration.resource.key", change.resourceKey())
                .log("Migration resource changed");
        }
    }
}
