package io.taskmigo;

import io.taskmigo.user.UserService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.4-alpine");
    }

    @Bean
    ApplicationRunner persistedSystemUserFixture(UserService users, PasswordEncoder passwordEncoder) {
        return arguments -> {
            if (!users.reconcileSystemUser(passwordEncoder.encode("integration-password"))) {
                throw new IllegalStateException("Failed to create persisted system-user test fixture");
            }
        };
    }
}
