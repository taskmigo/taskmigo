package io.taskmigo.migration.application.service;

import io.taskmigo.migration.application.model.InstallationChange;
import io.taskmigo.migration.application.model.InstallationPlan;
import io.taskmigo.migration.application.model.ManagedOAuthClient;
import io.taskmigo.migration.application.port.out.ManagedClientRegistry;
import io.taskmigo.migration.application.port.out.PasswordHasher;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Reconciles desired OAuth client state through the application-owned client registry port.
final class InternalClientReconciler {

    private final ManagedClientRegistry clients;
    private final PasswordHasher passwords;

    InternalClientReconciler(ManagedClientRegistry clients, PasswordHasher passwords) {
        this.clients = clients;
        this.passwords = passwords;
    }

    void validate(Map<String, InstallationPlan.OAuthClient> configuredClients) {
        Set<String> clientIds = new HashSet<>();
        configuredClients.values().forEach(client -> {
            if (!clientIds.add(client.clientId())) {
                throw new IllegalStateException("Duplicate internal client-id: " + client.clientId());
            }
        });
    }

    void reconcile(Map<String, InstallationPlan.OAuthClient> configuredClients, List<InstallationChange> changes) {
        configuredClients
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(configuredClient -> this.reconcile(configuredClient, changes));
    }

    private void reconcile(
        Map.Entry<String, InstallationPlan.OAuthClient> configuredClient,
        List<InstallationChange> changes
    ) {
        InstallationPlan.OAuthClient definition = configuredClient.getValue();
        var existing = this.clients.findByClientId(definition.clientId());

        if (existing.isPresent() && !existing.orElseThrow().managed()) {
            throw new IllegalStateException("Refusing to adopt unmanaged OAuth client: " + definition.clientId());
        }

        String encodedSecret = this.encodedSecret(definition.rawSecret(), existing.orElse(null));
        String id = existing.isPresent() ? existing.orElseThrow().id() : configuredClient.getKey();
        ManagedOAuthClient desired = ManagedOAuthClient.from(id, encodedSecret, definition);

        InstallationChange.Action action = existing.isEmpty()
            ? InstallationChange.Action.ADDED
            : this.clients.matches(desired)
              ? InstallationChange.Action.UNCHANGED
              : InstallationChange.Action.UPDATED;
        if (action != InstallationChange.Action.UNCHANGED) {
            this.clients.save(desired);
            changes.add(new InstallationChange("oauth-client", definition.clientId(), action));
        }
    }

    private String encodedSecret(String rawSecret, @Nullable ManagedClientRegistry.ExistingClient existing) {
        if (existing != null) {
            var encodedSecret = existing.encodedSecret();
            if (encodedSecret != null && this.passwords.matches(rawSecret, encodedSecret)) {
                return encodedSecret;
            }
        }
        return this.passwords.hash(rawSecret);
    }
}
