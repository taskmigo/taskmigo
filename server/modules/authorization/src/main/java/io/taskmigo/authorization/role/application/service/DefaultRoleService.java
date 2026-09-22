package io.taskmigo.authorization.role.application.service;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.application.port.in.api.RoleService;
import io.taskmigo.authorization.role.application.port.in.internal.RoleCommandService;
import io.taskmigo.authorization.role.application.port.out.RoleHierarchyRepository;
import io.taskmigo.authorization.role.application.port.out.RoleQueryRepository;
import io.taskmigo.authorization.role.domain.RoleRuleViolation;
import io.taskmigo.authorization.role.domain.hierarchy.RoleHierarchy;
import io.taskmigo.authorization.role.domain.hierarchy.RoleHierarchyException;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Coordinates runtime Role profile, hierarchy, and read use cases behind explicit ports.
public final class DefaultRoleService implements RoleService {

    private final RoleCommandService commands;
    private final RoleQueryRepository roles;
    private final RoleHierarchyRepository hierarchies;
    private final TransactionRunner transactions;

    public DefaultRoleService(
        RoleCommandService commands,
        RoleQueryRepository roles,
        RoleHierarchyRepository hierarchies,
        TransactionRunner transactions
    ) {
        this.commands = commands;
        this.roles = roles;
        this.hierarchies = hierarchies;
        this.transactions = transactions;
    }

    @Override
    public UUID createRole(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        @Nullable Collection<UUID> childRoleIds
    ) {
        return this.transactions.write(() -> {
            Set<UUID> requestedChildIds = childRoleIds == null ? Set.of() : Set.copyOf(childRoleIds);
            RoleHierarchy current = this.hierarchies.loadForMutation();
            requireChildren(requestedChildIds, current);

            UUID id;
            try {
                id = this.commands.createRuntime(code, displayName, description);
            } catch (RoleRuleViolation exception) {
                throw invalidInput(exception);
            }

            RoleHierarchy requested;
            try {
                requested = current.replacingChildren(id, requestedChildIds);
            } catch (RoleHierarchyException exception) {
                throw new RoleException(RoleException.Type.INVALID_INPUT, hierarchyFailureMessage(exception));
            }

            this.hierarchies.replaceChildren(id, requestedChildIds, requested);
            return id;
        });
    }

    @Override
    public void requireRoles(Collection<UUID> ids) {
        this.transactions.read(() -> {
            this.requireRolesDirect(ids);
            return true;
        });
    }

    @Override
    public OffsetPage<RoleInfo> listRoles(
        int page,
        int perPage,
        QueryPredicate<RoleInfo> filter,
        ObjectAuthorizationPredicate<RoleInfo> authorization
    ) {
        return this.transactions.read(() -> this.roles.list(page, perPage, filter, authorization));
    }

    @Override
    public void setChildRoles(UUID parentRoleId, Collection<UUID> childRoleIds) {
        this.transactions.write(() -> {
            Set<UUID> requestedIds = Set.copyOf(childRoleIds);
            RoleHierarchy current = this.hierarchies.loadForMutation();
            requireRole(parentRoleId, current);
            requireChildren(requestedIds, current);

            RoleHierarchy requested;
            try {
                requested = current.replacingChildren(parentRoleId, requestedIds);
            } catch (RoleHierarchyException exception) {
                throw new RoleException(RoleException.Type.INVALID_INPUT, hierarchyFailureMessage(exception));
            }

            this.hierarchies.replaceChildren(parentRoleId, requestedIds, requested);
        });
    }

    @Override
    public List<RoleInfo> descendantRoles(UUID roleId) {
        return this.transactions.read(() -> {
            this.requireRolesDirect(Set.of(roleId));
            return this.effectiveRolesDirect(Set.of(roleId))
                .stream()
                .filter(role -> !role.id().equals(roleId))
                .toList();
        });
    }

    @Override
    public List<RoleInfo> effectiveRoles(Collection<UUID> roleIds) {
        return this.transactions.read(() -> this.effectiveRolesDirect(roleIds));
    }

    private List<RoleInfo> effectiveRolesDirect(Collection<UUID> roleIds) {
        Set<UUID> requestedIds = Set.copyOf(roleIds);
        if (requestedIds.isEmpty()) {
            return List.of();
        }
        this.requireRolesDirect(requestedIds);
        return this.roles
            .findByIds(this.hierarchies.descendantRoleIds(requestedIds))
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .toList();
    }

    private void requireRolesDirect(Collection<UUID> ids) {
        if (!this.roles.containsAll(Set.copyOf(ids))) {
            throw new RoleException(RoleException.Type.INVALID_INPUT, "One or more Roles do not exist");
        }
    }

    private static void requireRole(UUID id, RoleHierarchy hierarchy) {
        if (!hierarchy.contains(id)) {
            throw new RoleException(RoleException.Type.INVALID_INPUT, "Role does not exist");
        }
    }

    private static void requireChildren(Set<UUID> childIds, RoleHierarchy hierarchy) {
        if (!hierarchy.containsAll(childIds)) {
            throw new RoleException(RoleException.Type.INVALID_INPUT, "One or more child Roles do not exist");
        }
    }

    private static RoleException invalidInput(RoleRuleViolation exception) {
        return new RoleException(RoleException.Type.INVALID_INPUT, exception.detail());
    }

    private static String hierarchyFailureMessage(RoleHierarchyException exception) {
        String message = exception.getMessage();
        return message == null ? "Role hierarchy is invalid" : message;
    }
}
