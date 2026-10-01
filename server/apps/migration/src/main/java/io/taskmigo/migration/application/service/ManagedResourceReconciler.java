package io.taskmigo.migration.application.service;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.provisioning.application.port.in.api.AuthorizationProvisioningService;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.identity.provisioning.application.port.in.api.GroupProvisioningService;
import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.migration.application.model.InstallationPlan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;

/// Reconciles declarative Identity and Access Control resources through their provider-owned provisioning APIs.
final class ManagedResourceReconciler {

    private final AuthorizationProvisioningService authorization;
    private final IdentityProvisioningService identity;
    private final GroupProvisioningService groups;
    private final PasswordEncoder passwords;

    ManagedResourceReconciler(
        AuthorizationProvisioningService authorization,
        IdentityProvisioningService identity,
        GroupProvisioningService groups,
        PasswordEncoder passwords
    ) {
        this.authorization = authorization;
        this.identity = identity;
        this.groups = groups;
        this.passwords = passwords;
    }

    void validate(InstallationPlan data) {
        Map<String, InstallationPlan.Statement> statements = unique(
            data.statements(),
            InstallationPlan.Statement::code,
            "Statement"
        );
        Map<String, InstallationPlan.Role> roles = unique(data.roles(), InstallationPlan.Role::code, "Role");
        Map<String, InstallationPlan.Group> groups = unique(data.groups(), InstallationPlan.Group::code, "Group");
        unique(data.users(), InstallationPlan.User::username, "User");

        for (InstallationPlan.Role role : data.roles()) {
            if (role.absent()) {
                continue;
            }
            requireActiveReferences(role.statements(), statements, InstallationPlan.Statement::absent, "statement");
        }
        for (InstallationPlan.Group group : data.groups()) {
            if (group.absent()) {
                continue;
            }
            requireActiveReferences(group.roles(), roles, InstallationPlan.Role::absent, "role");
        }
        for (InstallationPlan.User user : data.users()) {
            if (user.absent()) {
                continue;
            }
            requireActiveReferences(user.roles(), roles, InstallationPlan.Role::absent, "role");
            requireActiveReferences(user.groups(), groups, InstallationPlan.Group::absent, "group");
        }
    }

    void reconcile(InstallationPlan data) {
        this.deleteAbsent(data);

        Map<String, UUID> statementIds = this.reconcileStatements(data.statements());
        Map<String, UUID> roleIds = this.reconcileRoles(data.roles(), statementIds);
        Map<String, UUID> groupIds = this.reconcileGroups(data.groups(), roleIds);
        this.reconcileUsers(data.users(), roleIds, groupIds);
    }

    private void deleteAbsent(InstallationPlan data) {
        data.users()
            .stream()
            .filter(InstallationPlan.User::absent)
            .forEach(user -> this.identity.deleteUser(user.username()));
        data.groups()
            .stream()
            .filter(InstallationPlan.Group::absent)
            .forEach(group -> this.groups.deleteGroup(group.code()));
        data.roles()
            .stream()
            .filter(InstallationPlan.Role::absent)
            .forEach(role -> this.authorization.deleteRole(role.code()));
        data.statements()
            .stream()
            .filter(InstallationPlan.Statement::absent)
            .forEach(statement -> this.authorization.deleteStatement(statement.code()));
    }

    private Map<String, UUID> reconcileStatements(List<InstallationPlan.Statement> definitions) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (InstallationPlan.Statement definition : definitions) {
            if (definition.absent()) {
                continue;
            }
            UUID id = this.authorization
                .reconcileStatement(
                    definition.code(),
                    definition.description(),
                    Effect.from(definition.effect()),
                    Scope.from(definition.scope()),
                    definition.target().api().method(),
                    definition.target().api().path(),
                    definition.policy()
                )
                .id();
            result.put(definition.code(), id);
        }
        return result;
    }

    private Map<String, UUID> reconcileRoles(List<InstallationPlan.Role> definitions, Map<String, UUID> statementIds) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (InstallationPlan.Role definition : definitions) {
            if (definition.absent()) {
                continue;
            }
            Set<UUID> ids = definition.statements().stream().map(statementIds::get).collect(Collectors.toSet());
            UUID id = this.authorization
                .reconcileRole(definition.code(), definition.displayName(), definition.description(), ids)
                .id();
            result.put(definition.code(), id);
        }
        return result;
    }

    private Map<String, UUID> reconcileGroups(List<InstallationPlan.Group> definitions, Map<String, UUID> roleIds) {
        Map<String, UUID> result = new LinkedHashMap<>();
        for (InstallationPlan.Group definition : definitions) {
            if (definition.absent()) {
                continue;
            }
            Set<UUID> ids = definition.roles().stream().map(roleIds::get).collect(Collectors.toSet());
            UUID id = this.groups
                .reconcileGroup(definition.code(), definition.displayName(), definition.description(), ids)
                .id();
            result.put(definition.code(), id);
        }
        return result;
    }

    private void reconcileUsers(
        List<InstallationPlan.User> definitions,
        Map<String, UUID> roleIds,
        Map<String, UUID> groupIds
    ) {
        for (InstallationPlan.User user : definitions) {
            if (user.absent()) {
                continue;
            }
            Set<UUID> roles = user.roles().stream().map(roleIds::get).collect(Collectors.toSet());
            Set<UUID> groups = user.groups().stream().map(groupIds::get).collect(Collectors.toSet());
            this.identity.reconcileUser(
                user.username(),
                this.initialPasswordHash(user.password()),
                user.emails(),
                user.firstName(),
                user.lastName(),
                roles,
                groups
            );
        }
    }

    private @Nullable String initialPasswordHash(@Nullable String rawPassword) {
        return rawPassword == null || rawPassword.isBlank() ? null : this.passwords.encode(rawPassword);
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
