package io.taskmigo.authorization.role.configuration;

import io.taskmigo.authorization.role.application.DefaultRoleAuthorizationService;
import io.taskmigo.authorization.role.application.DefaultRoleCommandService;
import io.taskmigo.authorization.role.application.DefaultRoleService;
import io.taskmigo.authorization.role.application.RoleCommandRepository;
import io.taskmigo.authorization.role.application.RoleHierarchyRepository;
import io.taskmigo.authorization.role.application.RoleQueryRepository;
import io.taskmigo.authorization.role.application.api.RoleAuthorizationService;
import io.taskmigo.authorization.role.application.api.RoleService;
import io.taskmigo.authorization.role.application.internal.RoleCommandService;
import io.taskmigo.authorization.statement.application.api.StatementService;
import io.taskmigo.authorization.transaction.TransactionRunner;
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
