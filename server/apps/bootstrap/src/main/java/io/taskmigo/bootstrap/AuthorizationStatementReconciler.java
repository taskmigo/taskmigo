package io.taskmigo.bootstrap;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningService;
import io.taskmigo.identity.provisioning.IdentityProvisioningService;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.dataformat.yaml.YAMLMapper;

/// Loads and reconciles the three-file built-in authorization dataset in dependency order.
@Component
@Order(2)
class AuthorizationStatementReconciler implements ApplicationRunner {

    private static final String RESOURCE_PREFIX = "bootstrap/authorization/";

    private final AuthorizationProvisioningService authorization;
    private final IdentityProvisioningService identity;
    private final YAMLMapper yaml = YAMLMapper.builder().build();

    AuthorizationStatementReconciler(
        AuthorizationProvisioningService authorization,
        IdentityProvisioningService identity
    ) {
        this.authorization = authorization;
        this.identity = identity;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) throws Exception {
        StatementsFile statementsFile = this.read("statements.yaml", StatementsFile.class);
        RolesFile rolesFile = this.read("roles.yaml", RolesFile.class);
        UsersFile usersFile = this.read("users.yaml", UsersFile.class);
        Map<String, UUID> statementIds = this.reconcileStatements(values(statementsFile.statements()));
        Map<String, UUID> roleIds = this.reconcileRoles(values(rolesFile.roles()), statementIds);
        for (User user : values(usersFile.users())) {
            this.reconcileUser(user, roleIds, statementIds);
        }
    }

    private <T> T read(String filename, Class<T> type) throws Exception {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PREFIX + filename);
        try (InputStream input = resource.getInputStream()) {
            return this.yaml.readValue(input, type);
        }
    }

    private Map<String, UUID> reconcileStatements(List<Statement> definitions) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (Statement definition : definitions) {
            if (result.containsKey(definition.name())) {
                throw new AuthorizationProvisioningException(
                    "Duplicate managed authorization Statement: " + definition.name()
                );
            }
            result.put(
                definition.name(),
                this.authorization.reconcileStatement(
                    definition.name(),
                    definition.description(),
                    definition.effect(),
                    definition.scope(),
                    definition.target().api().method(),
                    definition.target().api().path(),
                    definition.policy()
                )
            );
        }
        return result;
    }

    private Map<String, UUID> reconcileRoles(List<Role> definitions, Map<String, UUID> statementIds) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (Role definition : definitions) {
            if (result.containsKey(definition.name())) {
                throw new AuthorizationProvisioningException(
                    "Duplicate managed authorization Role: " + definition.name()
                );
            }
            List<UUID> ids = values(definition.statements())
                .stream()
                .map(name -> this.resolveStatement(statementIds, name))
                .toList();
            result.put(
                definition.name(),
                this.authorization.reconcileRole(definition.name(), definition.description(), ids)
            );
        }
        return result;
    }

    private void reconcileUser(User user, Map<String, UUID> roleIds, Map<String, UUID> statementIds) {
        Set<UUID> roles = values(user.roles())
            .stream()
            .map(roleName -> this.resolveRole(roleIds, roleName))
            .collect(Collectors.toSet());
        Set<UUID> statements = values(user.statements())
            .stream()
            .map(statementName -> this.resolveStatement(statementIds, statementName))
            .collect(Collectors.toSet());
        this.identity.reconcileUser(
            user.username(),
            user.email(),
            user.firstName(),
            user.lastName(),
            roles,
            statements
        );
    }

    private UUID resolveStatement(Map<String, UUID> values, String name) {
        return values.computeIfAbsent(name, this.authorization::requireStatement);
    }

    private UUID resolveRole(Map<String, UUID> values, String name) {
        return values.computeIfAbsent(name, this.authorization::requireRole);
    }

    private static <T> List<T> values(@Nullable List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private record StatementsFile(@Nullable List<Statement> statements) {
        private StatementsFile {
            statements = values(statements);
        }
    }

    private record RolesFile(@Nullable List<Role> roles) {
        private RolesFile {
            roles = values(roles);
        }
    }

    private record UsersFile(@Nullable List<User> users) {
        private UsersFile {
            users = values(users);
        }
    }

    private record Statement(
        String name,
        String description,
        String effect,
        String scope,
        Target target,
        String policy
    ) {}

    private record Target(Api api) {}

    private record Api(String method, String path) {}

    private record Role(String name, String description, @Nullable List<String> statements) {
        private Role {
            statements = values(statements);
        }
    }

    private record User(
        String username,
        List<String> email,
        String firstName,
        String lastName,
        List<String> roles,
        List<String> statements
    ) {}
}
