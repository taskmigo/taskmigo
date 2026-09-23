package io.taskmigo.migration.application.service;

import io.taskmigo.authorization.provisioning.application.port.in.api.AuthorizationProvisioningService;
import io.taskmigo.identity.provisioning.application.port.in.api.GroupProvisioningService;
import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.migration.application.model.InstallationChange;
import io.taskmigo.migration.application.model.InstallationPlan;
import io.taskmigo.migration.application.port.in.InstallationService;
import io.taskmigo.migration.application.port.out.InstallationChangePublisher;
import io.taskmigo.migration.application.port.out.InstallationTransaction;
import io.taskmigo.migration.application.port.out.ManagedClientRegistry;
import io.taskmigo.migration.application.port.out.PasswordHasher;
import java.util.ArrayList;
import java.util.List;

/// Implements the installation use case while keeping transaction, credential, and persistence mechanics behind ports.
public final class DefaultInstallationService implements InstallationService {

    private static final int MAX_RECONCILIATION_ATTEMPTS = 3;

    private final ManagedResourceReconciler resources;
    private final InternalClientReconciler clients;
    private final InstallationTransaction transactions;
    private final InstallationChangePublisher changes;

    /// Creates the installation application service.
    ///
    /// @param authorization Access Control provisioning port
    /// @param identity Identity User provisioning port
    /// @param groups Identity Group provisioning port
    /// @param clients managed OAuth client persistence port
    /// @param passwords credential hashing port
    /// @param transactions installation transaction port
    /// @param changes post-commit change publisher
    public DefaultInstallationService(
        AuthorizationProvisioningService authorization,
        IdentityProvisioningService identity,
        GroupProvisioningService groups,
        ManagedClientRegistry clients,
        PasswordHasher passwords,
        InstallationTransaction transactions,
        InstallationChangePublisher changes
    ) {
        this.resources = new ManagedResourceReconciler(authorization, identity, groups, passwords);
        this.clients = new InternalClientReconciler(clients, passwords);
        this.transactions = transactions;
        this.changes = changes;
    }

    @Override
    public void install(InstallationPlan plan) {
        this.resources.validate(plan);
        this.clients.validate(plan.clients());

        List<InstallationChange> committedChanges = this.transactions.serializable(MAX_RECONCILIATION_ATTEMPTS, () -> {
            List<InstallationChange> attemptChanges = new ArrayList<>();
            this.resources.reconcile(plan, attemptChanges);
            this.clients.reconcile(plan.clients(), attemptChanges);
            return List.copyOf(attemptChanges);
        });
        this.changes.publish(committedChanges);
    }
}
