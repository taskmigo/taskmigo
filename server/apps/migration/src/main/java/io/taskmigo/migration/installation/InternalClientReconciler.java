package io.taskmigo.migration.installation;

import io.taskmigo.audit.application.record.AuditRecorder;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.audit.model.AuditOperation;
import io.taskmigo.migration.oauth.ManagedClientRepository;
import io.taskmigo.migration.oauth.ManagedOAuthClient;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;

/// Reconciles desired OAuth client state against Spring Authorization Server persistence.
final class InternalClientReconciler {

    private final ManagedClientRepository clients;
    private final PasswordEncoder passwords;
    private final AuditRecorder audits;

    InternalClientReconciler(ManagedClientRepository clients, PasswordEncoder passwords, AuditRecorder audits) {
        this.clients = clients;
        this.passwords = passwords;
        this.audits = audits;
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

        boolean created = existing.isEmpty();
        if (created || !this.clients.matches(desired)) {
            this.clients.save(desired);
            this.audits.record(
                "oauth-client",
                definition.clientId(),
                created ? AuditOperation.CREATE : AuditOperation.UPDATE,
                () ->
                    created
                        ? List.of(
                              AuditChange.visible("clientId", null, definition.clientId()),
                              AuditChange.sensitive("clientSecret"),
                              AuditChange.sensitive("configuration")
                          )
                        : List.of(AuditChange.sensitive("clientSecret"), AuditChange.sensitive("configuration"))
            );
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
