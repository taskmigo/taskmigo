package io.taskmigo.identity;

import io.taskmigo.identity.InternalClientProperties.Definition;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
final class InternalClientReconciler implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalClientReconciler.class);
    private static final long RECONCILIATION_LOCK_ID = 827_319_409L;

    private final InternalClientProperties properties;
    private final JdbcRegisteredClientRepository clients;
    private final InternalRegisteredClientFactory clientFactory;
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;

    InternalClientReconciler(
        InternalClientProperties properties,
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
        reconcile(properties.clients());
    }

    void reconcile(List<Definition> definitions) {
        validate(definitions);
        transactions.executeWithoutResult(status -> {
            jdbc.execute("select pg_advisory_xact_lock(" + RECONCILIATION_LOCK_ID + ")");
            definitions.stream().sorted(Comparator.comparing(Definition::id)).forEach(this::reconcile);
        });
    }

    private void validate(List<Definition> definitions) {
        Set<String> clientIds = new HashSet<>();
        definitions.forEach(definition -> {
            if (!clientIds.add(definition.id())) {
                throw new IllegalStateException("Duplicate internal client-id: " + definition.id());
            }
        });
    }

    private void reconcile(Definition definition) {
        String definitionHash = InternalClientMetadata.definitionHash(definition);
        @Nullable
        RegisteredClient existing = clients.findByClientId(definition.id());

        if (existing != null && !InternalClientMetadata.isManaged(existing)) {
            throw new IllegalStateException("Refusing to adopt unmanaged OAuth client: " + definition.id());
        }
        long currentGeneration = existing == null ? 0 : InternalClientMetadata.generation(existing);
        if (currentGeneration > definition.generation()) {
            LOGGER.info(
                "Ignoring stale internal client definition {} at generation {}; database is at generation {}",
                definition.id(),
                definition.generation(),
                currentGeneration
            );
            return;
        }
        if (
            existing != null &&
            currentGeneration == definition.generation() &&
            !InternalClientMetadata.definitionHash(existing).equals(definitionHash)
        ) {
            throw new IllegalStateException(
                "Internal client definition changed without increasing generation: " + definition.id()
            );
        }

        boolean secretRotationAllowed = existing == null || definition.generation() > currentGeneration;
        RegisteredClient desired = clientFactory.create(definition, existing, secretRotationAllowed, definitionHash);
        clients.save(desired);
    }
}
