package io.taskmigo.authorization.provisioning.configuration;

import io.taskmigo.authorization.provisioning.application.api.AuthorizationProvisioningService;
import io.taskmigo.authorization.provisioning.application.service.DefaultAuthorizationProvisioningService;
import io.taskmigo.authorization.role.application.RoleHierarchyRepository;
import io.taskmigo.authorization.role.application.internal.RoleCommandService;
import io.taskmigo.authorization.statement.application.api.StatementService;
import io.taskmigo.authorization.statement.application.internal.StatementCommandService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuthorizationProvisioningApplicationConfiguration {

    @Bean
    AuthorizationProvisioningService defaultAuthorizationProvisioningService(
        RoleCommandService roles,
        RoleHierarchyRepository hierarchies,
        StatementService statementService,
        StatementCommandService statementCommands
    ) {
        return new DefaultAuthorizationProvisioningService(roles, hierarchies, statementService, statementCommands);
    }
}
