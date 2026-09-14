package io.taskmigo.migration;

import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.identity.authorization.role.RoleAuthorizationService;
import io.taskmigo.identity.authorization.role.RoleService;
import io.taskmigo.identity.authorization.statement.StatementService;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserService;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.dataformat.yaml.YAMLMapper;

/// Loads and reconciles the built-in authorization dataset in dependency order.
@Component
@Order(2)
class AuthorizationStatementReconciler implements ApplicationRunner {

    private static final String RESOURCE_PREFIX = "migration/authorization/";

    private final MigrationProperties properties;
    private final StatementService statements;
    private final RoleAuthorizationService access;
    private final RoleService roles;
    private final UserService users;
    private final PasswordEncoder passwordEncoder;
    private final YAMLMapper yaml = YAMLMapper.builder().build();

    AuthorizationStatementReconciler(
        MigrationProperties properties,
        StatementService statements,
        RoleAuthorizationService access,
        RoleService roles,
        UserService users,
        PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.statements = statements;
        this.access = access;
        this.roles = roles;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) throws Exception {
        StatementsFile statementsFile = this.read("statements.yaml", StatementsFile.class);
        RolesFile rolesFile = this.read("roles.yaml", RolesFile.class);
        UsersFile usersFile = this.read("users.yaml", UsersFile.class);
        MigrationProperties.User system = Objects.requireNonNull(
            this.properties.user(),
            "taskmigo.migration.user must be configured"
        );
        String systemPasswordHash = this.systemPasswordHash(system.password());
        for (User user : values(usersFile.users())) {
            if (SystemUser.USERNAME.equals(user.username().trim())) {
                throw new IllegalStateException(
                    "System user must be configured under taskmigo.migration.user, not users.yaml"
                );
            }
        }
        Map<String, UUID> statementIds = this.reconcileStatements(values(statementsFile.statements()));
        Map<String, UUID> roleIds = this.reconcileRoles(values(rolesFile.roles()), statementIds);
        for (User user : values(usersFile.users())) {
            this.reconcileUser(user, roleIds, statementIds, null);
        }
        this.reconcileUser(
            new User(
                SystemUser.USERNAME,
                system.emails(),
                system.firstName(),
                system.lastName(),
                system.roles(),
                system.statements()
            ),
            roleIds,
            statementIds,
            systemPasswordHash
        );
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
                throw new IllegalStateException("Duplicate built-in authorization Statement: " + definition.name());
            }
            result.put(
                definition.name(),
                this.statements.reconcile(
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
                throw new IllegalStateException("Duplicate built-in authorization Role: " + definition.name());
            }
            List<UUID> ids = values(definition.statements())
                .stream()
                .map(name -> this.resolveStatement(statementIds, name))
                .toList();
            result.put(definition.name(), this.access.reconcile(definition.name(), definition.description(), ids));
        }
        return result;
    }

    private void reconcileUser(
        User user,
        Map<String, UUID> roleIds,
        Map<String, UUID> statementIds,
        @Nullable String passwordHash
    ) {
        Set<UUID> roles = values(user.roles())
            .stream()
            .map(roleName -> this.resolveRole(roleIds, roleName))
            .collect(Collectors.toSet());
        Set<UUID> statements = values(user.statements())
            .stream()
            .map(statementName -> this.resolveStatement(statementIds, statementName))
            .collect(Collectors.toSet());
        this.users.reconcileManagedUser(
            user.username(),
            user.email(),
            user.firstName(),
            user.lastName(),
            roles,
            statements,
            passwordHash
        );
    }

    private String systemPasswordHash(@Nullable String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("taskmigo.migration.user.password must not be blank");
        }
        return this.passwordEncoder.encode(password);
    }

    private UUID resolveStatement(Map<String, UUID> values, String name) {
        return values.computeIfAbsent(name, this.statements::requireByName);
    }

    private UUID resolveRole(Map<String, UUID> values, String name) {
        return values.computeIfAbsent(name, this.roles::requireRoleByName);
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
        Effect effect,
        Scope scope,
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
