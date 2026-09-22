package io.taskmigo.authorization.role.application.service;

import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.application.port.in.internal.RoleCommandService;
import io.taskmigo.authorization.role.application.port.in.internal.RoleMutationResult;
import io.taskmigo.authorization.role.application.port.out.RoleCommandRepository;
import io.taskmigo.authorization.role.application.port.out.RoleHierarchyRepository;
import io.taskmigo.authorization.role.domain.Role;
import io.taskmigo.authorization.role.domain.RoleCode;
import io.taskmigo.authorization.role.domain.hierarchy.RoleHierarchy;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Applies canonical Role mutations through domain-shaped command and hierarchy ports.
public final class DefaultRoleCommandService implements RoleCommandService {

    private final RoleCommandRepository roles;
    private final RoleHierarchyRepository hierarchies;

    public DefaultRoleCommandService(RoleCommandRepository roles, RoleHierarchyRepository hierarchies) {
        this.roles = roles;
        this.hierarchies = hierarchies;
    }

    @Override
    public UUID createRuntime(@Nullable String code, @Nullable String displayName, @Nullable String description) {
        Role role = Role.create(UUID.randomUUID(), code, displayName, description, Set.of());
        this.roles.save(role);
        return role.id();
    }

    @Override
    public RoleMutationResult reconcileManaged(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> statementIds
    ) {
        RoleCode normalizedCode = RoleCode.of(code);
        Role existing = this.roles.findByCode(normalizedCode).orElse(null);
        if (existing == null) {
            Role created = Role.create(
                UUID.randomUUID(),
                normalizedCode.value(),
                displayName,
                description,
                statementIds
            );
            this.roles.save(created);
            return new RoleMutationResult(created.id(), true, true);
        }

        boolean changed = existing.reconcile(displayName, description, statementIds);
        if (changed) {
            this.roles.save(existing);
        }
        return new RoleMutationResult(existing.id(), false, changed);
    }

    @Override
    public Optional<Role> findByCode(@Nullable String code) {
        return this.roles.findByCode(RoleCode.of(code));
    }

    @Override
    public void replaceStatements(UUID roleId, Collection<UUID> statementIds) {
        Role role = this.roles
            .find(roleId)
            .orElseThrow(() -> new RoleException(RoleException.Type.INVALID_INPUT, "Role does not exist"));
        if (role.replaceStatements(statementIds)) {
            this.roles.save(role);
        }
    }

    @Override
    public void delete(Role role) {
        RoleHierarchy current = this.hierarchies.loadForMutation();
        RoleHierarchy remaining = current.removing(role.id());
        this.hierarchies.remove(role.id(), remaining);
        this.roles.delete(role);
    }
}
