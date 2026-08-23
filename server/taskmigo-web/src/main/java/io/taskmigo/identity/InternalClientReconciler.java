package io.taskmigo.identity;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
final class InternalClientReconciler implements ApplicationRunner {

    private static final long RECONCILIATION_LOCK_ID = 827_319_409L;

    private final OAuth2AuthorizationServerProperties properties;
    private final JdbcRegisteredClientRepository clients;
    private final InternalRegisteredClientFactory clientFactory;
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;

    InternalClientReconciler(
        OAuth2AuthorizationServerProperties properties,
        JdbcRegisteredClientRepository clients,
        InternalRegisteredClientFactory clientFactory,
        JdbcOperations jdbc,
        PlatformTransactionManager transactionManager
    ) {
        this.properties = properties;
        this.clients = clients;
        this.clientFactory = clientFactory;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments arguments) {
        reconcile(properties.getClient());
    }

    void reconcile(Map<String, Client> configuredClients) {
        validate(configuredClients);
        transactions.executeWithoutResult(status -> {
            jdbc.execute("select pg_advisory_xact_lock(" + RECONCILIATION_LOCK_ID + ")");
            configuredClients.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(this::reconcile);
        });
    }

    private void validate(Map<String, Client> configuredClients) {
        Set<String> clientIds = new HashSet<>();
        configuredClients.values().forEach(client -> {
            String clientId = clientId(client);
            if (!clientIds.add(clientId)) {
                throw new IllegalStateException("Duplicate internal client-id: " + clientId);
            }
        });
    }

    private void reconcile(Map.Entry<String, Client> configuredClient) {
        String clientId = clientId(configuredClient.getValue());
        @Nullable
        RegisteredClient existing = clients.findByClientId(clientId);

        if (existing != null && !InternalClientMetadata.isManaged(existing)) {
            throw new IllegalStateException("Refusing to adopt unmanaged OAuth client: " + clientId);
        }
        clients.save(clientFactory.create(configuredClient.getKey(), configuredClient.getValue(), existing));
    }

    private String clientId(Client client) {
        return Objects.requireNonNull(client.getRegistration().getClientId());
    }
}
