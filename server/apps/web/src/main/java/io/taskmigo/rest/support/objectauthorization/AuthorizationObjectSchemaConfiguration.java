package io.taskmigo.rest.support.objectauthorization;

import io.taskmigo.auth.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationSchemaRegistration;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationSchemaRegistry;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.group.GroupInfo;
import io.taskmigo.auth.role.RoleInfo;
import io.taskmigo.auth.user.UserInfo;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/// Registers the API routes whose collection responses support database-side Object Authorization.
@Configuration(proxyBeanMethods = false)
class AuthorizationObjectSchemaConfiguration {

    /// Maps each versioned collection endpoint to its resource-owned object contract.
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
