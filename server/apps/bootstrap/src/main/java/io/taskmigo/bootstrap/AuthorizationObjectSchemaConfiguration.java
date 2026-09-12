package io.taskmigo.bootstrap;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistration;
import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistry;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.identity.user.UserInfo;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/// Registers bootstrap-time resource routes needed to validate managed Object Statements.
@Configuration(proxyBeanMethods = false)
class AuthorizationObjectSchemaConfiguration {

    /// Supplies the same collection resource mapping used by the web application.
    @Bean
    @Primary
    ObjectAuthorizationSchemaRegistry objectAuthorizationSchemaRegistry(
        ObjectAuthorizationSchema<UserInfo> users,
        ObjectAuthorizationSchema<GroupInfo> groups,
        ObjectAuthorizationSchema<RoleInfo> roles,
        ObjectAuthorizationSchema<StatementInfo> statements
    ) {
        return ObjectAuthorizationSchemaRegistry.of(
            List.of(
                new ObjectAuthorizationSchemaRegistration("GET", "/api/v0/users", users),
                new ObjectAuthorizationSchemaRegistration("GET", "/api/v0/groups", groups),
                new ObjectAuthorizationSchemaRegistration("GET", "/api/v0/roles", roles),
                new ObjectAuthorizationSchemaRegistration("GET", "/api/v0/statements", statements)
            )
        );
    }
}
