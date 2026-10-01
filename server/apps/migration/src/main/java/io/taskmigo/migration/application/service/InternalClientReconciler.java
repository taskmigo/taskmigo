package io.taskmigo.migration.application.service;

import io.taskmigo.migration.application.model.InstallationPlan;
import io.taskmigo.migration.application.model.ManagedOAuthClient;
import io.taskmigo.migration.infrastructure.oauth.ManagedClientRepository;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;

/// Reconciles desired OAuth client state against Spring Authorization Server persistence.
final class InternalClientReconciler {

    private final ManagedClientRepository clients;
    private final PasswordEncoder passwords;

    InternalClientReconciler(ManagedClientRepository clients, PasswordEncoder passwords) {
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

    void reconcile(Map<String, InstallationPlan.OAuthClient> configuredClients) {
        configuredClients.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(this::reconcile);
    }

    private void reconcile(Map.Entry<String, InstallationPlan.OAuthClient> configuredClient) {
        InstallationPlan.OAuthClient definition = configuredClient.getValue();
        var existing = this.clients.findByClientId(definition.clientId());

        if (existing.isPresent() && !existing.orElseThrow().managed()) {
            throw new IllegalStateException("Refusing to adopt unmanaged OAuth client: " + definition.clientId());
        }

        String encodedSecret = this.encodedSecret(definition.rawSecret(), existing.orElse(null));
        String id = existing.isPresent() ? existing.orElseThrow().id() : configuredClient.getKey();
        ManagedOAuthClient desired = ManagedOAuthClient.from(id, encodedSecret, definition);

        if (existing.isEmpty() || !this.clients.matches(desired)) {
            this.clients.save(desired);
        }
    }

    private String encodedSecret(String rawSecret, ManagedClientRepository.@Nullable ExistingClient existing) {
        if (existing != null) {
            var encodedSecret = existing.encodedSecret();
            if (encodedSecret != null && this.passwords.matches(rawSecret, encodedSecret)) {
                return encodedSecret;
            }
        }
        return this.passwords.encode(rawSecret);
    }
}
