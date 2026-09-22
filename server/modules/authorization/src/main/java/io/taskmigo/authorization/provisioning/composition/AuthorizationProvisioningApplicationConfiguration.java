package io.taskmigo.authorization.provisioning.composition;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.provisioning.application.port.in.api.AuthorizationProvisioningService;
import io.taskmigo.authorization.provisioning.application.service.DefaultAuthorizationProvisioningService;
import io.taskmigo.authorization.role.application.port.in.internal.RoleCommandService;
import io.taskmigo.authorization.role.application.port.out.RoleHierarchyRepository;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import io.taskmigo.authorization.statement.application.port.in.internal.StatementCommandService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuthorizationProvisioningApplicationConfiguration {

    @Bean
    AuthorizationProvisioningService defaultAuthorizationProvisioningService(
        RoleCommandService roles,
        RoleHierarchyRepository hierarchies,
        StatementService statementService,
        StatementCommandService statementCommands,
        TransactionRunner transactions
    ) {
        return new DefaultAuthorizationProvisioningService(
            roles,
            hierarchies,
            statementService,
            statementCommands,
            transactions
        );
    }
}
