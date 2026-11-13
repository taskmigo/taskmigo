package io.taskmigo.migration.installation;

import io.taskmigo.audit.application.record.AuditRecorder;
import io.taskmigo.audit.context.AuditCause;
import io.taskmigo.audit.context.AuditContext;
import io.taskmigo.audit.context.AuditContexts;
import io.taskmigo.audit.context.AuditExecutor;
import io.taskmigo.authorization.provisioning.application.api.AuthorizationProvisioningService;
import io.taskmigo.identity.provisioning.application.api.GroupProvisioningService;
import io.taskmigo.identity.provisioning.application.api.IdentityProvisioningService;
import io.taskmigo.migration.oauth.ManagedClientRepository;
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
        PlatformTransactionManager transactionManager,
        AuditRecorder audits
    ) {
        this.resources = new ManagedResourceReconciler(authorization, identity, groups, passwords);
        this.clients = new InternalClientReconciler(clients, passwords, audits);
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    public void install(InstallationPlan plan) {
        this.resources.validate(plan);
        this.clients.validate(plan.clients());
        AuditContexts.run(
            AuditContext.system(
                "taskmigo-migration",
                AuditExecutor.migration("migration"),
                AuditCause.installation("startup")
            ),
            () -> this.reconcile(plan)
        );
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
