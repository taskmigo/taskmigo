package io.taskmigo.migration;

import io.taskmigo.identity.oauth.InternalClientMetadata;
import io.taskmigo.identity.oauth.RegisteredClientRepository;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/// Reconciles migration-managed browser and machine OAuth clients into the persistent registry.
@Component
final class RegisteredClientReconciler implements ApplicationRunner {

    private static final int MAX_RECONCILIATION_ATTEMPTS = 3;

    private final MigrationProperties properties;
    private final RegisteredClientRepository clients;
    private final RegisteredClientFactory clientFactory;
    private final TransactionTemplate transactions;

    RegisteredClientReconciler(
        MigrationProperties properties,
        RegisteredClientRepository clients,
        RegisteredClientFactory clientFactory,
        PlatformTransactionManager transactionManager
    ) {
        this.properties = properties;
        this.clients = clients;
        this.clientFactory = clientFactory;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    @Override
    public void run(ApplicationArguments arguments) {
        this.reconcile(this.properties.registeredClients());
    }

    void reconcile(Map<String, MigrationProperties.ManagedClientProperties> configuredClients) {
        this.validate(configuredClients);
        for (int attempt = 1; ; attempt++) {
            try {
                this.transactions.executeWithoutResult(status ->
                    configuredClients.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(this::reconcile)
                );
                return;
            } catch (TransientDataAccessException | DataIntegrityViolationException exception) {
                if (attempt == MAX_RECONCILIATION_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }

    private void reconcile(Map.Entry<String, MigrationProperties.ManagedClientProperties> configuredClient) {
        String clientId = Objects.requireNonNull(configuredClient.getValue().getRegistration().getClientId());
        RegisteredClient existing = this.clients.findByClientId(clientId);
        if (existing != null && !this.isManaged(clientId, existing)) {
            throw new IllegalStateException("Refusing to adopt or remove unmanaged OAuth client: " + clientId);
        }
        if (configuredClient.getValue().isAbsent()) {
            if (existing != null) {
                this.clients.deleteById(existing.getId());
            }
            return;
        }
        this.clients.save(this.clientFactory.create(configuredClient.getKey(), configuredClient.getValue(), existing));
    }

    private boolean isManaged(String clientId, RegisteredClient existing) {
        return BrowserClientMetadata.CLIENT_ID.equals(clientId)
            ? BrowserClientMetadata.isManaged(existing)
            : InternalClientMetadata.isManaged(existing);
    }

    private void validate(Map<String, MigrationProperties.ManagedClientProperties> configuredClients) {
        Set<String> clientIds = new HashSet<>();
        configuredClients.forEach((registrationId, definition) -> {
            RegisteredClientFactory.validate(registrationId, definition);
            String clientId = Objects.requireNonNull(definition.getRegistration().getClientId());
            if (!clientIds.add(clientId)) {
                throw new IllegalStateException("Duplicate registered client-id: " + clientId);
            }
        });
    }
}
