package io.taskmigo.bootstrap;

import io.taskmigo.user.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/// Ensures the persistent platform bootstrap user exists after database migration.
@Component
final class SystemUserReconciler implements ApplicationRunner {

    private static final int MAX_RECONCILIATION_ATTEMPTS = 3;

    private final BootstrapUserProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final UserService users;

    SystemUserReconciler(BootstrapUserProperties properties, PasswordEncoder passwordEncoder, UserService users) {
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
                if (!this.users.reconcileSystemUser(initialPasswordHash)) {
                    throw new IllegalStateException(
                        "TASKMIGO_BOOTSTRAP_USER_PASSWORD must be set when the system user has not been initialized"
                    );
                }
                return;
            } catch (TransientDataAccessException | DataIntegrityViolationException exception) {
                if (attempt == MAX_RECONCILIATION_ATTEMPTS) throw exception;
            }
        }
    }
}
