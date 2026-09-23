package io.taskmigo.migration.composition;

import io.taskmigo.authorization.provisioning.application.port.in.api.AuthorizationProvisioningService;
import io.taskmigo.identity.provisioning.application.port.in.api.GroupProvisioningService;
import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.migration.application.port.in.InstallationService;
import io.taskmigo.migration.application.port.out.InstallationChangePublisher;
import io.taskmigo.migration.application.port.out.InstallationTransaction;
import io.taskmigo.migration.application.port.out.ManagedClientRegistry;
import io.taskmigo.migration.application.port.out.PasswordHasher;
import io.taskmigo.migration.application.service.DefaultInstallationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;

@Configuration(proxyBeanMethods = false)
class MigrationConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    JdbcRegisteredClientRepository registeredClients(JdbcOperations jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    @Bean
    InstallationService installationService(
        AuthorizationProvisioningService authorization,
        IdentityProvisioningService identity,
        GroupProvisioningService groups,
        ManagedClientRegistry clients,
        PasswordHasher passwords,
        InstallationTransaction transactions,
        InstallationChangePublisher changes
    ) {
        return new DefaultInstallationService(
            authorization,
            identity,
            groups,
            clients,
            passwords,
            transactions,
            changes
        );
    }
}
