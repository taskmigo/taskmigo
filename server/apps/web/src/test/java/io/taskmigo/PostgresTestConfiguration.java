package io.taskmigo;

import io.taskmigo.identity.oauth.InternalClientMetadata;
import io.taskmigo.user.UserService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.4-alpine");
    }

    @Bean
    ApplicationRunner persistedRuntimeStateFixture(
        UserService users,
        PasswordEncoder passwordEncoder,
        JdbcRegisteredClientRepository clients
    ) {
        return arguments -> {
            if (!users.reconcileSystemUser(passwordEncoder.encode("integration-password"))) {
                throw new IllegalStateException("Failed to create persisted system-user test fixture");
            }
            if (clients.findByClientId("integration-client") == null) {
                clients.save(
                    RegisteredClient.withId("integration-client")
                        .clientId("integration-client")
                        .clientSecret(passwordEncoder.encode("integration-secret"))
                        .clientName("Integration client")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .scope(InternalClientMetadata.API_SCOPE)
                        .clientSettings(InternalClientMetadata.settings(false, false))
                        .build()
                );
            }
        };
    }
}
