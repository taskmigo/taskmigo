package io.taskmigo.identity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
final class BrowserClientReconciler implements ApplicationRunner {

    private static final int MAX_RECONCILIATION_ATTEMPTS = 3;

    private final BrowserAuthenticationProperties properties;
    private final JdbcRegisteredClientRepository clients;
    private final BrowserRegisteredClientFactory clientFactory;
    private final TransactionTemplate transactions;

    BrowserClientReconciler(
        BrowserAuthenticationProperties properties,
        JdbcRegisteredClientRepository clients,
        BrowserRegisteredClientFactory clientFactory,
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
        if (!this.properties.enabled()) return;
        this.reconcile();
    }

    void reconcile() {
        for (int attempt = 1; ; attempt++) {
            try {
                this.transactions.executeWithoutResult(status -> this.reconcileClient());
                return;
            } catch (TransientDataAccessException | DataIntegrityViolationException exception) {
                if (attempt == MAX_RECONCILIATION_ATTEMPTS) throw exception;
            }
        }
    }

    private void reconcileClient() {
        RegisteredClient existing = this.clients.findByClientId(BrowserClientMetadata.CLIENT_ID);
        if (existing != null && !BrowserClientMetadata.isManaged(existing)) {
            throw new IllegalStateException(
                "Refusing to adopt unmanaged browser OAuth client: " + BrowserClientMetadata.CLIENT_ID
            );
        }
        this.clients.save(this.clientFactory.create(this.properties, existing));
    }
}
