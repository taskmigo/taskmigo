package io.taskmigo.identity;

import io.taskmigo.identity.IdentityProperties.InternalClientDefinition;
import io.taskmigo.identity.InternalClientRepository.ManagedRegistration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
final class InternalClientReconciler implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalClientReconciler.class);
    private static final long RECONCILIATION_LOCK_ID = 827_319_409L;

    private final IdentityProperties properties;
    private final InternalClientRepository clients;
    private final InternalRegisteredClientFactory clientFactory;
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;

    InternalClientReconciler(
        IdentityProperties properties,
        InternalClientRepository clients,
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
        reconcile(properties.internalClients());
    }

    void reconcile(Map<String, InternalClientDefinition> definitions) {
        transactions.executeWithoutResult(status -> {
            jdbc.execute("select pg_advisory_xact_lock(" + RECONCILIATION_LOCK_ID + ")");
            definitions
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> reconcile(entry.getKey(), entry.getValue()));
        });
    }

    private void reconcile(String registrationKey, InternalClientDefinition definition) {
        InternalClientDefinitionValidator.validate(registrationKey, definition);
        String definitionHash = InternalClientFingerprint.calculate(definition);
        @Nullable
        ManagedRegistration managed = clients.findManagedRegistration(registrationKey).orElse(null);
        @Nullable
        RegisteredClient existing = managed == null ? null : clients.findById(managed.registeredClientId());

        if (managed != null && existing == null) {
            throw new IllegalStateException("Managed OAuth client is missing: " + registrationKey);
        }
        if (managed != null && managed.configurationVersion() > definition.configurationVersion()) {
            LOGGER.info(
                "Ignoring stale internal client definition {} at version {}; database is at version {}",
                registrationKey,
                definition.configurationVersion(),
                managed.configurationVersion()
            );
            return;
        }
        if (
            managed != null &&
            managed.configurationVersion() == definition.configurationVersion() &&
            !managed.definitionHash().equals(definitionHash)
        ) {
            throw new IllegalStateException(
                "Internal client definition changed without increasing configuration-version: " + registrationKey
            );
        }
        if (existing == null && clients.findByClientId(definition.clientId()) != null) {
            throw new IllegalStateException("Refusing to adopt unmanaged OAuth client: " + definition.clientId());
        }

        boolean secretRotationAllowed =
            managed == null || definition.configurationVersion() > managed.configurationVersion();
        RegisteredClient desired = clientFactory.create(registrationKey, definition, existing, secretRotationAllowed);
        clients.save(desired);
        clients.saveManagement(registrationKey, definition, definitionHash, desired.getId());
        clients.replaceServicePrincipal(definition, desired.getId());
    }
}
