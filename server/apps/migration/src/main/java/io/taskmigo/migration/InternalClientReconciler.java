package io.taskmigo.migration;

import io.taskmigo.foundation.ReconciliationAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/// Reconciles configured internal OAuth clients into the persistent client registry.
@Component
@Order(2)
final class InternalClientReconciler implements ApplicationRunner {

    private static final int MAX_RECONCILIATION_ATTEMPTS = 3;

    private final MigrationResourceLoader resources;
    private final JdbcRegisteredClientRepository clients;
    private final InternalRegisteredClientFactory clientFactory;
    private final MigrationChangeLogger changeLogger;
    private final TransactionTemplate transactions;

    InternalClientReconciler(
        MigrationResourceLoader resources,
        JdbcRegisteredClientRepository clients,
        InternalRegisteredClientFactory clientFactory,
        MigrationChangeLogger changeLogger,
        PlatformTransactionManager transactionManager
    ) {
        this.resources = resources;
        this.clients = clients;
        this.clientFactory = clientFactory;
        this.changeLogger = changeLogger;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    @Override
    public void run(ApplicationArguments arguments) {
        this.reconcile(this.resources.load().clients());
    }

    void reconcile(Map<String, Client> configuredClients) {
        this.validate(configuredClients);
        for (int attempt = 1; ; attempt++) {
            List<MigrationChange> changes = new ArrayList<>();
            try {
                this.transactions.executeWithoutResult(status ->
                    configuredClients
                        .entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(configuredClient -> this.reconcile(configuredClient, changes))
                );
                this.changeLogger.log(changes);
                return;
            } catch (TransientDataAccessException | DataIntegrityViolationException exception) {
                if (attempt == MAX_RECONCILIATION_ATTEMPTS) {
                    throw exception;
                }
            }
        }
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
        ReconciliationAction action =
            existing == null
                ? ReconciliationAction.ADDED
                : sameManagedData(existing, requested)
                  ? ReconciliationAction.UNCHANGED
                  : ReconciliationAction.UPDATED;
        if (action != ReconciliationAction.UNCHANGED) {
            this.clients.save(requested);
            changes.add(new MigrationChange("oauth-client", clientId, action));
        }
    }

    private void validate(Map<String, Client> configuredClients) {
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
