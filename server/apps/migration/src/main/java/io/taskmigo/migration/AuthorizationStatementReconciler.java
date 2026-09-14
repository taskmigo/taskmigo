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
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.dataformat.yaml.YAMLMapper;

/// Loads and reconciles the built-in authorization dataset in dependency order.
@Component
@Order(2)
class AuthorizationStatementReconciler implements ApplicationRunner {

    private static final String RESOURCE_PREFIX = "migration/authorization/";

    private final StatementService statements;
    private final RoleAuthorizationService access;
    private final RoleService roles;
    private final UserService users;
    private final Environment environment;
    private final YAMLMapper yaml = YAMLMapper.builder().build();

    AuthorizationStatementReconciler(
        StatementService statements,
        RoleAuthorizationService access,
        RoleService roles,
        UserService users,
        Environment environment
    ) {
        this.statements = statements;
        this.access = access;
        this.roles = roles;
        this.users = users;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) throws Exception {
        StatementsFile statementsFile = this.read("statements.yaml", StatementsFile.class);
        RolesFile rolesFile = this.read("roles.yaml", RolesFile.class);
        UsersFile usersFile = this.resolve(this.read("users.yaml", UsersFile.class));
        List<User> configuredUsers = values(usersFile.users());
        List<User> systems = configuredUsers
            .stream()
            .filter(user -> SystemUser.USERNAME.equals(user.username().trim()))
            .toList();
        if (systems.size() != 1) {
            throw new IllegalStateException("users.yaml must define exactly one system user");
        }
        User system = systems.getFirst();
        String systemPasswordHash = this.systemPassword(system.password());
        Map<String, UUID> statementIds = this.reconcileStatements(values(statementsFile.statements()));
        Map<String, UUID> roleIds = this.reconcileRoles(values(rolesFile.roles()), statementIds);
        for (User user : configuredUsers) {
            if (!SystemUser.USERNAME.equals(user.username().trim())) {
                if (user.password() != null && !user.password().isBlank()) {
                    throw new IllegalStateException("Only the system user may define a password in users.yaml");
                }
                this.reconcileUser(user, roleIds, statementIds, null);
            }
        }
        this.reconcileUser(system, roleIds, statementIds, systemPasswordHash);
    }

    private <T> T read(String filename, Class<T> type) throws Exception {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PREFIX + filename);
        try (InputStream input = resource.getInputStream()) {
            return this.yaml.readValue(input, type);
        }
    }

    private UsersFile resolve(UsersFile usersFile) {
        return new UsersFile(
            values(usersFile.users())
                .stream()
                .map(user ->
                    new User(
                        this.resolveRequired(user.username()),
                        this.resolve(user.password()),
                        this.resolveList(user.email()),
                        this.resolveRequired(user.firstName()),
                        this.resolveRequired(user.lastName()),
                        this.resolveList(user.roles()),
                        this.resolveList(user.statements())
                    )
                )
                .toList()
        );
    }

    private @Nullable String resolve(@Nullable String value) {
        return value == null ? null : this.environment.resolveRequiredPlaceholders(value);
    }

    private String resolveRequired(String value) {
        return Objects.requireNonNull(this.resolve(value));
    }

    private List<String> resolveList(@Nullable List<String> values) {
        return values(values).stream().map(this::resolve).map(Objects::requireNonNull).toList();
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

    private String systemPassword(@Nullable String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("users.yaml system password must not be blank");
        }
        if (!password.startsWith("{") || !password.contains("}")) {
            throw new IllegalStateException("users.yaml system password must be an encoded password");
        }
        return password;
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
        @Nullable String password,
        List<String> email,
        String firstName,
        String lastName,
        List<String> roles,
        List<String> statements
    ) {
        private User {
            email = values(email);
            roles = values(roles);
            statements = values(statements);
        }
    }
}
