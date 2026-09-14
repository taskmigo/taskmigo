package io.taskmigo.migration;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;

/// Describes the installation data reconciled by the migration application.
@ConfigurationProperties(prefix = "taskmigo.migration")
record MigrationProperties(User user, Map<String, ManagedClientProperties> registeredClients) {
    MigrationProperties {
        registeredClients = registeredClients == null ? Map.of() : Map.copyOf(registeredClients);
    }

    record User(
        String password,
        List<String> emails,
        String firstName,
        String lastName,
        List<String> roles,
        List<String> statements
    ) {
        User {
            emails = values(emails);
            roles = values(roles);
            statements = values(statements);
        }
    }

    /// Extends Spring Boot's authorization-server client properties with migration lifecycle metadata.
    static final class ManagedClientProperties extends OAuth2AuthorizationServerProperties.Client {

        private boolean absent;

        public boolean isAbsent() {
            return this.absent;
        }

        public void setAbsent(boolean absent) {
            this.absent = absent;
        }
    }

    private static <T> List<T> values(@Nullable List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
