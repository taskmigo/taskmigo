package io.taskmigo.migration;

import io.taskmigo.security.oauth.RegisteredClientType;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;

/// Describes the installation data reconciled by the migration application.
@ConfigurationProperties(prefix = "taskmigo")
record MigrationProperties(Map<String, ManagedClientProperties> registeredClients) {
    MigrationProperties {
        registeredClients = registeredClients == null ? Map.of() : Map.copyOf(registeredClients);
    }

    /// Extends Spring Boot's authorization-server client properties with migration lifecycle fields.
    static final class ManagedClientProperties extends OAuth2AuthorizationServerProperties.Client {

        private String type = RegisteredClientType.INTERNAL;

        private boolean absent;

        public String getType() {
            return this.type;
        }

        public void setType(String type) {
            this.type = RegisteredClientType.requireValid(type);
        }

        public boolean isAbsent() {
            return this.absent;
        }

        public void setAbsent(boolean absent) {
            this.absent = absent;
        }
    }
}
