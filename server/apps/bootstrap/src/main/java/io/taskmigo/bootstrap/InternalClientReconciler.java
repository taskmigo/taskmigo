package io.taskmigo.bootstrap;

import io.taskmigo.identity.oauth.InternalClientMetadata;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
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
final class InternalClientReconciler implements ApplicationRunner {

    private static final int MAX_RECONCILIATION_ATTEMPTS = 3;

    private final OAuth2AuthorizationServerProperties properties;
    private final JdbcRegisteredClientRepository clients;
    private final InternalRegisteredClientFactory clientFactory;
    private final TransactionTemplate transactions;

    InternalClientReconciler(
        OAuth2AuthorizationServerProperties properties,
        JdbcRegisteredClientRepository clients,
        InternalRegisteredClientFactory clientFactory,
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
        this.reconcile(this.properties.getClient());
    }

    void reconcile(Map<String, Client> configuredClients) {
        this.validate(configuredClients);
        for (int attempt = 1; ; attempt++) {
            try {
                this.transactions.executeWithoutResult(status ->
                    configuredClients.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(this::reconcile)
                );
                return;
            } catch (TransientDataAccessException | DataIntegrityViolationException exception) {
                if (attempt == MAX_RECONCILIATION_ATTEMPTS) throw exception;
            }
        }
    }

    private void reconcile(Map.Entry<String, Client> configuredClient) {
        String clientId = this.clientId(configuredClient.getValue());
        RegisteredClient existing = this.clients.findByClientId(clientId);

        if (existing != null && !InternalClientMetadata.isManaged(existing)) {
            throw new IllegalStateException("Refusing to adopt unmanaged OAuth client: " + clientId);
        }
        this.clients.save(this.clientFactory.create(configuredClient.getKey(), configuredClient.getValue(), existing));
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
}
