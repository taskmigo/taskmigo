package io.taskmigo.authorization.role.composition;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.role.application.port.in.api.RoleAuthorizationService;
import io.taskmigo.authorization.role.application.port.in.api.RoleService;
import io.taskmigo.authorization.role.application.port.in.internal.RoleCommandService;
import io.taskmigo.authorization.role.application.port.out.RoleCommandRepository;
import io.taskmigo.authorization.role.application.port.out.RoleHierarchyRepository;
import io.taskmigo.authorization.role.application.port.out.RoleQueryRepository;
import io.taskmigo.authorization.role.application.service.DefaultRoleAuthorizationService;
import io.taskmigo.authorization.role.application.service.DefaultRoleCommandService;
import io.taskmigo.authorization.role.application.service.DefaultRoleService;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RoleApplicationConfiguration {

    @Bean
    RoleCommandService defaultRoleCommandService(RoleCommandRepository roles, RoleHierarchyRepository hierarchies) {
        return new DefaultRoleCommandService(roles, hierarchies);
    }

    @Bean
    RoleService defaultRoleService(
        RoleCommandService commands,
        RoleQueryRepository roles,
        RoleHierarchyRepository hierarchies,
        TransactionRunner transactions
    ) {
        return new DefaultRoleService(commands, roles, hierarchies, transactions);
    }

    @Bean
    RoleAuthorizationService defaultRoleAuthorizationService(
        RoleCommandService roles,
        StatementService statements,
        TransactionRunner transactions
    ) {
        return new DefaultRoleAuthorizationService(roles, statements, transactions);
    }
}
