package io.taskmigo;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningService;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.identity.provisioning.IdentityProvisioningService;
import io.taskmigo.identity.user.UserService;
import io.taskmigo.security.oauth.RegisteredClientDefinition;
import io.taskmigo.security.oauth.RegisteredClientRepository;
import io.taskmigo.security.oauth.RegisteredClientType;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
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
        UserService users,
        PasswordEncoder passwordEncoder,
        RegisteredClientRepository clients
    ) {
        return arguments -> {
            identity.reconcileSystemUser(passwordEncoder.encode("integration-password"));
            UUID fullAccess = authorization.reconcileStatement(
                "system_operator_request_all",
                "Allows the system administrator to access the versioned API.",
                Effect.ALLOW,
                Scope.REQUEST,
                "*",
                "/api/v.*/.*",
                "return true;"
            );
            UUID usersAccess = objectStatement(authorization, "system_users_full_access", "/api/v0/users");
            UUID rolesAccess = objectStatement(authorization, "system_roles_full_access", "/api/v0/roles");
            UUID groupsAccess = objectStatement(authorization, "system_groups_full_access", "/api/v0/groups");
            UUID statementsAccess = objectStatement(
                authorization,
                "system_statements_full_access",
                "/api/v0/statements"
            );
            UUID roleId = authorization.reconcileRole(
                "System Operator",
                "Highest-privilege integration-test role.",
                List.of(fullAccess, usersAccess, rolesAccess, groupsAccess, statementsAccess)
            );
            users.setRoles(users.findForAuthentication("system").orElseThrow().id(), List.of(roleId));
            if (clients.findByClientId("integration-client") == null) {
                clients.save(
                    new RegisteredClientDefinition(
                        RegisteredClient.withId("integration-client")
                            .clientId("integration-client")
                            .clientSecret(passwordEncoder.encode("integration-secret"))
                            .clientName("Integration client")
                            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                            .scope("taskmigo.api")
                            .clientSettings(
                                ClientSettings.builder().requireProofKey(false).requireAuthorizationConsent(false).build()
                            )
                            .build(),
                        RegisteredClientType.INTERNAL
                    )
                );
            }
        };
    }

    private static UUID objectStatement(AuthorizationProvisioningService authorization, String name, String path) {
        return authorization.reconcileStatement(
            name,
            "Allows the system administrator to view every object.",
            Effect.ALLOW,
            Scope.OBJECT,
            "GET",
            path,
            "return true;"
        );
    }
}
