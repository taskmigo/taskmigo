package io.taskmigo.migration.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;

/// Defines the framework components used directly by the migration executable.
@Configuration(proxyBeanMethods = false)
class MigrationConfiguration {

    @Bean
    JdbcRegisteredClientRepository registeredClients(JdbcOperations jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }
}
