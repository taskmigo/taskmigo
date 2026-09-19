package io.taskmigo.migration;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningService;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.identity.group.GroupService;
import io.taskmigo.identity.provisioning.IdentityProvisioningService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/// Reconciles flat authorization and identity resources in dependency order.
@Component
@Order(1)
final class AuthorizationStatementReconciler implements ApplicationRunner {

    private final AuthorizationProvisioningService authorization;
    private final IdentityProvisioningService identity;
    private final GroupService groups;
    private final MigrationResourceLoader resources;
    private final TransactionTemplate transactions;

    AuthorizationStatementReconciler(
        AuthorizationProvisioningService authorization,
        IdentityProvisioningService identity,
        GroupService groups,
        MigrationResourceLoader resources,
        PlatformTransactionManager transactionManager
    ) {
        this.authorization = authorization;
        this.identity = identity;
        this.groups = groups;
        this.resources = resources;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments arguments) {
        this.transactions.executeWithoutResult(status -> {
            MigrationResourceLoader.MigrationResources data = this.resources.load();
            this.validate(data);

            this.deleteAbsent(data);

            Map<String, UUID> statementIds = this.reconcileStatements(data.statements());
            Map<String, UUID> roleIds = this.reconcileRoles(data.roles(), statementIds);
            Map<String, UUID> groupIds = this.reconcileGroups(data.groups(), roleIds);
            this.reconcileUsers(data.users(), roleIds, groupIds);
        });
    }

    private void validate(MigrationResourceLoader.MigrationResources data) {
        Map<String, MigrationResourceLoader.Statement> statements = unique(
            data.statements(),
            MigrationResourceLoader.Statement::code,
            "Statement"
        );
        Map<String, MigrationResourceLoader.Role> roles = unique(
            data.roles(),
            MigrationResourceLoader.Role::code,
            "Role"
        );
        Map<String, MigrationResourceLoader.Group> groups = unique(
            data.groups(),
            MigrationResourceLoader.Group::code,
            "Group"
        );
        unique(data.users(), MigrationResourceLoader.User::username, "User");

        for (MigrationResourceLoader.Role role : data.roles()) {
            if (!role.absent()) {
                requireActiveReferences(
                    role.statements(),
                    statements,
                    MigrationResourceLoader.Statement::absent,
                    "statement"
                );
            }
        }
        for (MigrationResourceLoader.Group group : data.groups()) {
            if (!group.absent()) {
                requireActiveReferences(group.roles(), roles, MigrationResourceLoader.Role::absent, "role");
            }
        }
        for (MigrationResourceLoader.User user : data.users()) {
            if (!user.absent()) {
                requireActiveReferences(user.roles(), roles, MigrationResourceLoader.Role::absent, "role");
                requireActiveReferences(user.groups(), groups, MigrationResourceLoader.Group::absent, "group");
            }
        }
    }

    private void deleteAbsent(MigrationResourceLoader.MigrationResources data) {
        data.users()
            .stream()
            .filter(MigrationResourceLoader.User::absent)
            .forEach(user -> this.identity.deleteUser(user.username()));
        data.groups()
            .stream()
            .filter(MigrationResourceLoader.Group::absent)
            .forEach(group -> this.groups.deleteByCode(group.code()));
        data.roles()
            .stream()
            .filter(MigrationResourceLoader.Role::absent)
            .forEach(role -> this.authorization.deleteRole(role.code()));
        data.statements()
            .stream()
            .filter(MigrationResourceLoader.Statement::absent)
            .forEach(statement -> this.authorization.deleteStatement(statement.code()));
    }

    private Map<String, UUID> reconcileStatements(List<MigrationResourceLoader.Statement> definitions) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (MigrationResourceLoader.Statement definition : definitions) {
            if (!definition.absent()) {
                result.put(
                    definition.code(),
                    this.authorization.reconcileStatement(
                        definition.code(),
                        definition.description(),
                        Effect.from(definition.effect()),
                        Scope.from(definition.scope()),
                        definition.target().api().method(),
                        definition.target().api().path(),
                        definition.policy()
                    )
                );
            }
        }
        return result;
    }

    private Map<String, UUID> reconcileRoles(
        List<MigrationResourceLoader.Role> definitions,
        Map<String, UUID> statementIds
    ) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (MigrationResourceLoader.Role definition : definitions) {
            if (!definition.absent()) {
                Set<UUID> ids = definition.statements().stream().map(statementIds::get).collect(Collectors.toSet());
                result.put(
                    definition.code(),
                    this.authorization.reconcileRole(
                        definition.code(),
                        definition.displayName(),
                        definition.description(),
                        ids
                    )
                );
            }
        }
        return result;
    }

    private Map<String, UUID> reconcileGroups(
        List<MigrationResourceLoader.Group> definitions,
        Map<String, UUID> roleIds
    ) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (MigrationResourceLoader.Group definition : definitions) {
            if (!definition.absent()) {
                Set<UUID> ids = definition.roles().stream().map(roleIds::get).collect(Collectors.toSet());
                result.put(
                    definition.code(),
                    this.groups.reconcile(definition.code(), definition.displayName(), definition.description(), ids)
                );
            }
        }
        return result;
    }

    private void reconcileUsers(
        List<MigrationResourceLoader.User> definitions,
        Map<String, UUID> roleIds,
        Map<String, UUID> groupIds
    ) {
        for (MigrationResourceLoader.User user : definitions) {
            if (!user.absent()) {
                Set<UUID> roles = user.roles().stream().map(roleIds::get).collect(Collectors.toSet());
                Set<UUID> groups = user.groups().stream().map(groupIds::get).collect(Collectors.toSet());
                this.identity.reconcileUser(
                    user.username(),
                    user.password(),
                    user.emails(),
                    user.firstName(),
                    user.lastName(),
                    roles,
                    groups
                );
            }
        }
    }

    private static <T> Map<String, T> unique(List<T> values, Function<T, String> key, String type) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String code = key.apply(value);
            if (result.putIfAbsent(code, value) != null) {
                throw new AuthorizationProvisioningException("Duplicate managed " + type + ": " + code);
            }
        }
        return result;
    }

    private static <T> void requireActiveReferences(
        List<String> references,
        Map<String, T> definitions,
        Function<T, Boolean> absent,
        String type
    ) {
        for (String reference : references) {
            T definition = definitions.get(reference);
            if (definition == null || absent.apply(definition)) {
                throw new AuthorizationProvisioningException(
                    "Managed " + type + " reference does not resolve to an active resource: " + reference
                );
            }
        }
    }
}
