package io.taskmigo;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningService;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.identity.provisioning.IdentityProvisioningService;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
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
        IdentityProvisioningService identity,
        AuthorizationProvisioningService authorization,
        JdbcRegisteredClientRepository clients
    ) {
        return arguments -> {
            UUID fullAccess = authorization
                .reconcileStatement(
                    "system_operator_request_all",
                    "Allows the system administrator to access the versioned API.",
                    Effect.ALLOW,
                    Scope.REQUEST,
                    "*",
                    "/api/v.*/.*",
                    "return true;"
                )
                .id();
            UUID usersAccess = objectStatement(authorization, "system_users_full_access", "/api/v0/users");
            UUID rolesAccess = objectStatement(authorization, "system_roles_full_access", "/api/v0/roles");
            UUID groupsAccess = objectStatement(authorization, "system_groups_full_access", "/api/v0/groups");
            UUID statementsAccess = objectStatement(
                authorization,
                "system_statements_full_access",
                "/api/v0/statements"
            );
            UUID roleId = authorization
                .reconcileRole(
                    "system-operator",
                    "System Operator",
                    "Highest-privilege integration-test role.",
                    List.of(fullAccess, usersAccess, rolesAccess, groupsAccess, statementsAccess)
                )
                .id();
            identity.reconcileUser(
                "system",
                "{noop}integration-password",
                List.of(),
                "System",
                "User",
                List.of(roleId),
                List.of()
            );
            if (clients.findByClientId("taskmigo-client") == null) {
                clients.save(
                    RegisteredClient.withId("taskmigo-client")
                        .clientId("taskmigo-client")
                        .clientSecret("{noop}integration-secret")
                        .clientName("Taskmigo browser test client")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .clientSettings(
                            ClientSettings.builder()
                                .requireProofKey(false)
                                .requireAuthorizationConsent(false)
                                .setting("taskmigo.oauth-client.ownership", "internal")
                                .setting("taskmigo.internal-client.managed", "v1")
                                .build()
                        )
                        .build()
                );
            }
        };
    }

    private static UUID objectStatement(AuthorizationProvisioningService authorization, String code, String path) {
        return authorization
            .reconcileStatement(
                code,
                "Allows the system administrator to view every object.",
                Effect.ALLOW,
                Scope.OBJECT,
                "GET",
                path,
                "return true;"
            )
            .id();
    }
}
