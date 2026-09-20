package io.taskmigo.authorization.role.application;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.RoleService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates runtime Role profile, hierarchy, and read use cases.
@Service
public class DefaultRoleService implements RoleService {

    private final RoleCommandService commands;
    private final RoleQueryRepository roles;
    private final RoleHierarchyRepository hierarchies;

    public DefaultRoleService(
        RoleCommandService commands,
        RoleQueryRepository roles,
        RoleHierarchyRepository hierarchies
    ) {
        this.commands = commands;
        this.roles = roles;
        this.hierarchies = hierarchies;
    }

    @Override
    @Transactional
    public UUID createRole(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        @Nullable Collection<UUID> childRoleIds
    ) {
        Set<UUID> requestedChildIds = childRoleIds == null ? Set.of() : Set.copyOf(childRoleIds);
        RoleHierarchy current = this.hierarchies.loadForMutation();
        requireChildren(requestedChildIds, current);

        UUID id;
        try {
            id = this.commands.createRuntime(code, displayName, description);
        } catch (RoleRuleViolation exception) {
            throw badRequest(exception);
        }

        RoleHierarchy requested;
        try {
            requested = current.replacingChildren(id, requestedChildIds);
        } catch (RoleHierarchyException exception) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, hierarchyFailureMessage(exception));
        }

        this.hierarchies.replaceChildren(id, requestedChildIds, requested);
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireRoles(Collection<UUID> ids) {
        if (!this.roles.containsAll(Set.copyOf(ids))) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, "One or more Roles do not exist");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetPage<RoleInfo> listRoles(
        int page,
        int perPage,
        QueryPredicate<RoleInfo> filter,
        ObjectAuthorizationPredicate<RoleInfo> authorization
    ) {
        return this.roles.list(page, perPage, filter, authorization);
    }

    @Override
    @Transactional
    public void setChildRoles(UUID parentRoleId, Collection<UUID> childRoleIds) {
        Set<UUID> requestedIds = Set.copyOf(childRoleIds);
        RoleHierarchy current = this.hierarchies.loadForMutation();
        requireRole(parentRoleId, current);
        requireChildren(requestedIds, current);

        RoleHierarchy requested;
        try {
            requested = current.replacingChildren(parentRoleId, requestedIds);
        } catch (RoleHierarchyException exception) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, hierarchyFailureMessage(exception));
        }

        this.hierarchies.replaceChildren(parentRoleId, requestedIds, requested);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleInfo> descendantRoles(UUID roleId) {
        this.requireRoles(Set.of(roleId));
        return this.effectiveRoles(Set.of(roleId))
            .stream()
            .filter(role -> !role.id().equals(roleId))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRoles(Collection<UUID> roleIds) {
        Set<UUID> requestedIds = Set.copyOf(roleIds);
        if (requestedIds.isEmpty()) {
            return List.of();
        }
        this.requireRoles(requestedIds);
        return this.roles
            .findByIds(this.hierarchies.descendantRoleIds(requestedIds))
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .toList();
    }

    private static void requireRole(UUID id, RoleHierarchy hierarchy) {
        if (!hierarchy.contains(id)) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, "Role does not exist");
        }
    }

    private static void requireChildren(Set<UUID> childIds, RoleHierarchy hierarchy) {
        if (!hierarchy.containsAll(childIds)) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, "One or more child Roles do not exist");
        }
    }

    private static RoleException badRequest(RoleRuleViolation exception) {
        return new RoleException(RoleException.Type.BAD_REQUEST, exception.detail());
    }

    private static String hierarchyFailureMessage(RoleHierarchyException exception) {
        String message = exception.getMessage();
        return message == null ? "Role hierarchy is invalid" : message;
    }
}
