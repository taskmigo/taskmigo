package io.taskmigo.migration;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

/// Reconciles configured internal OAuth clients into the persistent client registry.
@Component
final class InternalClientReconciler {

    private final JdbcRegisteredClientRepository clients;
    private final InternalRegisteredClientFactory clientFactory;

    InternalClientReconciler(JdbcRegisteredClientRepository clients, InternalRegisteredClientFactory clientFactory) {
        this.clients = clients;
        this.clientFactory = clientFactory;
    }

    void reconcile(Map<String, Client> configuredClients, List<MigrationChange> changes) {
        configuredClients
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(configuredClient -> this.reconcile(configuredClient, changes));
    }

    private void reconcile(Map.Entry<String, Client> configuredClient, List<MigrationChange> changes) {
        String clientId = this.clientId(configuredClient.getValue());
        RegisteredClient existing = this.clients.findByClientId(clientId);

        if (existing != null && !InternalClientMetadata.isManaged(existing)) {
            throw new IllegalStateException("Refusing to adopt unmanaged OAuth client: " + clientId);
        }
        RegisteredClient requested = this.clientFactory.create(
            configuredClient.getKey(),
            configuredClient.getValue(),
            existing
        );
        MigrationChange.Action action =
            existing == null
                ? MigrationChange.Action.ADDED
                : sameManagedData(existing, requested)
                  ? MigrationChange.Action.UNCHANGED
                  : MigrationChange.Action.UPDATED;
        if (action != MigrationChange.Action.UNCHANGED) {
            this.clients.save(requested);
            changes.add(new MigrationChange("oauth-client", clientId, action));
        }
    }

    void validate(Map<String, Client> configuredClients) {
        Set<String> clientIds = new HashSet<>();
        configuredClients.values().forEach(client -> {
            String clientId = this.clientId(client);
            if (!clientIds.add(clientId)) {
                throw new IllegalStateException("Duplicate internal client-id: " + clientId);
            }
        });
    }

    private String clientId(Client client) {
        return Objects.requireNonNull(client.getRegistration().getClientId());
    }

    private static boolean sameManagedData(RegisteredClient current, RegisteredClient requested) {
        return (
            Objects.equals(current.getClientId(), requested.getClientId()) &&
            Objects.equals(current.getClientSecret(), requested.getClientSecret()) &&
            Objects.equals(current.getClientName(), requested.getClientName()) &&
            Objects.equals(current.getClientAuthenticationMethods(), requested.getClientAuthenticationMethods()) &&
            Objects.equals(current.getAuthorizationGrantTypes(), requested.getAuthorizationGrantTypes()) &&
            Objects.equals(current.getRedirectUris(), requested.getRedirectUris()) &&
            Objects.equals(current.getPostLogoutRedirectUris(), requested.getPostLogoutRedirectUris()) &&
            Objects.equals(current.getScopes(), requested.getScopes()) &&
            Objects.equals(current.getClientSettings(), requested.getClientSettings()) &&
            Objects.equals(current.getTokenSettings(), requested.getTokenSettings())
        );
    }
}
