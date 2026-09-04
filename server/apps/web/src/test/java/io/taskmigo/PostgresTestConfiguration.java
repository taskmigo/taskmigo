package io.taskmigo;

import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementService;
import io.taskmigo.auth.oauth.InternalClientMetadata;
import io.taskmigo.auth.role.RoleAuthorizationService;
import io.taskmigo.auth.user.UserService;
import java.util.UUID;
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
        RoleAuthorizationService access,
        StatementService statements,
        PasswordEncoder passwordEncoder,
        JdbcRegisteredClientRepository clients
    ) {
        return arguments -> {
            if (!users.reconcileSystemUser(passwordEncoder.encode("integration-password"))) {
                throw new IllegalStateException("Failed to create persisted system-user test fixture");
            }
            UUID fullAccess = statements.reconcile(
                "system_operator_request_all",
                "Allows the system administrator to access the versioned API.",
                Effect.ALLOW,
                Scope.REQUEST,
                "*",
                "/api/v.*/.*",
                null
            );
            UUID usersAccess = objectStatement(statements, "system_users_full_access", "/api/v0/users");
            UUID rolesAccess = objectStatement(statements, "system_roles_full_access", "/api/v0/roles");
            UUID groupsAccess = objectStatement(statements, "system_groups_full_access", "/api/v0/groups");
            UUID statementsAccess = objectStatement(statements, "system_statements_full_access", "/api/v0/statements");
            UUID roleId = access.reconcile(
                "System Operator",
                "Highest-privilege integration-test role.",
                java.util.List.of(fullAccess, usersAccess, rolesAccess, groupsAccess, statementsAccess)
            );
            users.setRoles(users.findForAuthentication("system").orElseThrow().id(), java.util.List.of(roleId));
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

    private static UUID objectStatement(StatementService statements, String name, String path) {
        return statements.reconcile(
            name,
            "Allows the system administrator to view every object.",
            Effect.ALLOW,
            Scope.OBJECT,
            "GET",
            path,
            null
        );
    }
}
