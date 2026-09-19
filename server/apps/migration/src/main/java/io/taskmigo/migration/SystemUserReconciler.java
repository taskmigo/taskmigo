package io.taskmigo.migration;

import io.taskmigo.identity.provisioning.IdentityProvisioningService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/// Ensures the persistent platform bootstrap user exists after database migration.
@Component
@Order(1)
final class SystemUserReconciler implements ApplicationRunner {

    private static final int MAX_RECONCILIATION_ATTEMPTS = 3;

    private final BootstrapUserProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProvisioningService users;

    SystemUserReconciler(
        BootstrapUserProperties properties,
        PasswordEncoder passwordEncoder,
        IdentityProvisioningService users
    ) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        this.reconcile();
    }

    void reconcile() {
        var password = this.properties.password();
        var initialPasswordHash = password == null || password.isBlank() ? null : this.passwordEncoder.encode(password);

        for (int attempt = 1; ; attempt++) {
            try {
                this.users.reconcileSystemUser(initialPasswordHash);
                return;
            } catch (TransientDataAccessException | DataIntegrityViolationException exception) {
                if (attempt == MAX_RECONCILIATION_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }
}
