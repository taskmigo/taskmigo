package io.taskmigo.migration;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningService;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.foundation.ReconciliationAction;
import io.taskmigo.foundation.ReconciliationResult;
import io.taskmigo.identity.group.GroupService;
import io.taskmigo.identity.provisioning.IdentityProvisioningService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/// Reconciles flat authorization and identity resources in dependency order.
@Component
final class AuthorizationStatementReconciler {

    private final AuthorizationProvisioningService authorization;
    private final IdentityProvisioningService identity;
    private final GroupService groups;
    private final PasswordEncoder passwordEncoder;

    AuthorizationStatementReconciler(
        AuthorizationProvisioningService authorization,
        IdentityProvisioningService identity,
        GroupService groups,
        PasswordEncoder passwordEncoder
    ) {
        this.authorization = authorization;
        this.identity = identity;
        this.groups = groups;
        this.passwordEncoder = passwordEncoder;
    }

    void reconcile(MigrationResourceLoader.MigrationResources data, List<MigrationChange> changes) {
        changes.addAll(this.deleteAbsent(data));

        Map<String, UUID> statementIds = this.reconcileStatements(data.statements(), changes);
        Map<String, UUID> roleIds = this.reconcileRoles(data.roles(), statementIds, changes);
        Map<String, UUID> groupIds = this.reconcileGroups(data.groups(), roleIds, changes);
        this.reconcileUsers(data.users(), roleIds, groupIds, changes);
    }

    void validate(MigrationResourceLoader.MigrationResources data) {
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
            if (role.absent()) {
                continue;
            }
            requireActiveReferences(
                role.statements(),
                statements,
                MigrationResourceLoader.Statement::absent,
                "statement"
            );
        }
        for (MigrationResourceLoader.Group group : data.groups()) {
            if (group.absent()) {
                continue;
            }
            requireActiveReferences(group.roles(), roles, MigrationResourceLoader.Role::absent, "role");
        }
        for (MigrationResourceLoader.User user : data.users()) {
            if (user.absent()) {
                continue;
            }
            requireActiveReferences(user.roles(), roles, MigrationResourceLoader.Role::absent, "role");
            requireActiveReferences(user.groups(), groups, MigrationResourceLoader.Group::absent, "group");
        }
    }

    private List<MigrationChange> deleteAbsent(MigrationResourceLoader.MigrationResources data) {
        List<MigrationChange> changes = new ArrayList<>();
        data.users()
            .stream()
            .filter(MigrationResourceLoader.User::absent)
            .forEach(user -> {
                if (this.identity.deleteUser(user.username())) {
                    changes.add(change("user", user.username(), ReconciliationAction.REMOVED));
                }
            });
        data.groups()
            .stream()
            .filter(MigrationResourceLoader.Group::absent)
            .forEach(group -> {
                if (this.groups.deleteByCode(group.code())) {
                    changes.add(change("group", group.code(), ReconciliationAction.REMOVED));
                }
            });
        data.roles()
            .stream()
            .filter(MigrationResourceLoader.Role::absent)
            .forEach(role -> {
                if (this.authorization.deleteRole(role.code())) {
                    changes.add(change("role", role.code(), ReconciliationAction.REMOVED));
                }
            });
        data.statements()
            .stream()
            .filter(MigrationResourceLoader.Statement::absent)
            .forEach(statement -> {
                if (this.authorization.deleteStatement(statement.code())) {
                    changes.add(change("statement", statement.code(), ReconciliationAction.REMOVED));
                }
            });
        return changes;
    }

    private Map<String, UUID> reconcileStatements(
        List<MigrationResourceLoader.Statement> definitions,
        List<MigrationChange> changes
    ) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (MigrationResourceLoader.Statement definition : definitions) {
            if (definition.absent()) {
                continue;
            }
            ReconciliationResult<UUID> reconciliation = this.authorization.reconcileStatement(
                definition.code(),
                definition.description(),
                Effect.from(definition.effect()),
                Scope.from(definition.scope()),
                definition.target().api().method(),
                definition.target().api().path(),
                definition.policy()
            );
            result.put(definition.code(), reconciliation.id());
            changes.add(change("statement", definition.code(), reconciliation.action()));
        }
        return result;
    }

    private Map<String, UUID> reconcileRoles(
        List<MigrationResourceLoader.Role> definitions,
        Map<String, UUID> statementIds,
        List<MigrationChange> changes
    ) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (MigrationResourceLoader.Role definition : definitions) {
            if (definition.absent()) {
                continue;
            }
            Set<UUID> ids = definition.statements().stream().map(statementIds::get).collect(Collectors.toSet());
            ReconciliationResult<UUID> reconciliation = this.authorization.reconcileRole(
                definition.code(),
                definition.displayName(),
                definition.description(),
                ids
            );
            result.put(definition.code(), reconciliation.id());
            changes.add(change("role", definition.code(), reconciliation.action()));
        }
        return result;
    }

    private Map<String, UUID> reconcileGroups(
        List<MigrationResourceLoader.Group> definitions,
        Map<String, UUID> roleIds,
        List<MigrationChange> changes
    ) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (MigrationResourceLoader.Group definition : definitions) {
            if (definition.absent()) {
                continue;
            }
            Set<UUID> ids = definition.roles().stream().map(roleIds::get).collect(Collectors.toSet());
            ReconciliationResult<UUID> reconciliation = this.groups.reconcile(
                definition.code(),
                definition.displayName(),
                definition.description(),
                ids
            );
            result.put(definition.code(), reconciliation.id());
            changes.add(change("group", definition.code(), reconciliation.action()));
        }
        return result;
    }

    private void reconcileUsers(
        List<MigrationResourceLoader.User> definitions,
        Map<String, UUID> roleIds,
        Map<String, UUID> groupIds,
        List<MigrationChange> changes
    ) {
        for (MigrationResourceLoader.User user : definitions) {
            if (user.absent()) {
                continue;
            }
            Set<UUID> roles = user.roles().stream().map(roleIds::get).collect(Collectors.toSet());
            Set<UUID> groups = user.groups().stream().map(groupIds::get).collect(Collectors.toSet());
            String initialPasswordHash = this.initialPasswordHash(user.password());
            ReconciliationResult<UUID> reconciliation = this.identity.reconcileUser(
                user.username(),
                initialPasswordHash,
                user.emails(),
                user.firstName(),
                user.lastName(),
                roles,
                groups
            );
            changes.add(change("user", user.username(), reconciliation.action()));
        }
    }

    private @org.jspecify.annotations.Nullable String initialPasswordHash(
        @org.jspecify.annotations.Nullable String rawPassword
    ) {
        return rawPassword == null || rawPassword.isBlank() ? null : this.passwordEncoder.encode(rawPassword);
    }

    private static MigrationChange change(String resourceType, String resourceKey, ReconciliationAction action) {
        return new MigrationChange(resourceType, resourceKey, action);
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
