package io.taskmigo.authorization.role.application;

import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.domain.Role;
import io.taskmigo.authorization.role.domain.RoleCode;
import io.taskmigo.authorization.role.domain.hierarchy.RoleHierarchy;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Applies canonical Role mutations through domain-shaped command and hierarchy ports.
@Service
public class DefaultRoleCommandService implements RoleCommandService {

    private final RoleCommandRepository roles;
    private final RoleHierarchyRepository hierarchies;

    public DefaultRoleCommandService(RoleCommandRepository roles, RoleHierarchyRepository hierarchies) {
        this.roles = roles;
        this.hierarchies = hierarchies;
    }

    @Override
    @Transactional
    public UUID createRuntime(@Nullable String code, @Nullable String displayName, @Nullable String description) {
        Role role = Role.create(UUID.randomUUID(), code, displayName, description, Set.of());
        this.roles.save(role);
        return role.id();
    }

    @Override
    @Transactional
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
    @Transactional(readOnly = true)
    public Optional<Role> findByCode(@Nullable String code) {
        return this.roles.findByCode(RoleCode.of(code));
    }

    @Override
    @Transactional
    public void replaceStatements(UUID roleId, Collection<UUID> statementIds) {
        Role role = this.roles
            .find(roleId)
            .orElseThrow(() -> new RoleException(RoleException.Type.BAD_REQUEST, "Role does not exist"));
        if (role.replaceStatements(statementIds)) {
            this.roles.save(role);
        }
    }

    @Override
    @Transactional
    public void delete(Role role) {
        RoleHierarchy current = this.hierarchies.loadForMutation();
        this.roles.delete(role);
        this.hierarchies.synchronize(current.removing(role.id()));
    }
}
