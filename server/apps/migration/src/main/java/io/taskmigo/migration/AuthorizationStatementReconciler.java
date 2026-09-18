package io.taskmigo.migration;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningService;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.identity.provisioning.IdentityProvisioningService;
import io.taskmigo.identity.user.SystemUser;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.dataformat.yaml.YAMLMapper;

/// Reconciles installation authorization and identity data through bounded-context provisioning contracts.
@Component
@Order(2)
class AuthorizationStatementReconciler implements ApplicationRunner {

    private static final String RESOURCE_PREFIX = "migration/authorization/";

    private final AuthorizationProvisioningService authorization;
    private final IdentityProvisioningService identity;
    private final Environment environment;
    private final PasswordEncoder passwordEncoder;
    private final YAMLMapper yaml = YAMLMapper.builder().build();

    AuthorizationStatementReconciler(
        AuthorizationProvisioningService authorization,
        IdentityProvisioningService identity,
        Environment environment,
        PasswordEncoder passwordEncoder
    ) {
        this.authorization = authorization;
        this.identity = identity;
        this.environment = environment;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) throws Exception {
        StatementsFile statementsFile = this.read("statements.yaml", StatementsFile.class);
        RolesFile rolesFile = this.read("roles.yaml", RolesFile.class);
        UsersFile usersFile = this.resolve(this.read("users.yaml", UsersFile.class));
        List<User> users = values(usersFile.users());

        List<User> systems = users.stream()
            .filter(user -> SystemUser.USERNAME.equals(user.username().trim()))
            .toList();
        if (systems.size() != 1) {
            throw new IllegalStateException("users.yaml must define exactly one system user");
        }
        this.identity.reconcileSystemUser(this.passwordHash(systems.getFirst().password()));

        Map<String, UUID> statementIds = this.reconcileStatements(values(statementsFile.statements()));
        Map<String, UUID> roleIds = this.reconcileRoles(values(rolesFile.roles()), statementIds);
        for (User user : users) {
            if (!SystemUser.USERNAME.equals(user.username().trim()) && user.password() != null && !user.password().isBlank()) {
                throw new IllegalStateException("Only the system user may define a password in users.yaml");
            }
            this.reconcileUser(user, roleIds, statementIds);
        }
    }

    private <T> T read(String filename, Class<T> type) throws Exception {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PREFIX + filename);
        try (InputStream input = resource.getInputStream()) {
            return this.yaml.readValue(input, type);
        }
    }

    private UsersFile resolve(UsersFile usersFile) {
        return new UsersFile(values(usersFile.users()).stream().map(user ->
            new User(
                this.resolveRequired(user.username()),
                this.resolve(user.password()),
                this.resolveList(user.email()),
                this.resolveRequired(user.firstName()),
                this.resolveRequired(user.lastName()),
                this.resolveList(user.roles()),
                this.resolveList(user.statements())
            )
        ).toList());
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

    private String passwordHash(@Nullable String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("users.yaml system password must not be blank");
        }
        if (password.startsWith("{") && password.contains("}")) {
            return password;
        }
        return this.passwordEncoder.encode(password);
    }

    private Map<String, UUID> reconcileStatements(List<Statement> definitions) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (Statement definition : definitions) {
            if (result.containsKey(definition.name())) {
                throw new AuthorizationProvisioningException("Duplicate managed authorization Statement: " + definition.name());
            }
            result.put(definition.name(), this.authorization.reconcileStatement(
                definition.name(), definition.description(), Effect.from(definition.effect()),
                Scope.from(definition.scope()), definition.target().api().method(),
                definition.target().api().path(), definition.policy()
            ));
        }
        return result;
    }

    private Map<String, UUID> reconcileRoles(List<Role> definitions, Map<String, UUID> statementIds) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (Role definition : definitions) {
            if (result.containsKey(definition.name())) {
                throw new AuthorizationProvisioningException("Duplicate managed authorization Role: " + definition.name());
            }
            List<UUID> ids = values(definition.statements()).stream()
                .map(name -> this.resolveStatement(statementIds, name)).toList();
            result.put(definition.name(), this.authorization.reconcileRole(definition.name(), definition.description(), ids));
        }
        return result;
    }

    private void reconcileUser(User user, Map<String, UUID> roleIds, Map<String, UUID> statementIds) {
        Set<UUID> roles = values(user.roles()).stream()
            .map(roleName -> this.resolveRole(roleIds, roleName)).collect(Collectors.toSet());
        Set<UUID> statements = values(user.statements()).stream()
            .map(statementName -> this.resolveStatement(statementIds, statementName)).collect(Collectors.toSet());
        this.identity.reconcileUser(user.username(), user.email(), user.firstName(), user.lastName(), roles, statements);
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
        private StatementsFile { statements = values(statements); }
    }
    private record RolesFile(@Nullable List<Role> roles) {
        private RolesFile { roles = values(roles); }
    }
    private record UsersFile(@Nullable List<User> users) {
        private UsersFile { users = values(users); }
    }
    private record Statement(String name, String description, String effect, String scope, Target target, String policy) {}
    private record Target(Api api) {}
    private record Api(String method, String path) {}
    private record Role(String name, String description, @Nullable List<String> statements) {
        private Role { statements = values(statements); }
    }
    private record User(
        String username, @Nullable String password, List<String> email, String firstName, String lastName,
        List<String> roles, List<String> statements
    ) {
        private User {
            email = values(email);
            roles = values(roles);
            statements = values(statements);
        }
    }
}
