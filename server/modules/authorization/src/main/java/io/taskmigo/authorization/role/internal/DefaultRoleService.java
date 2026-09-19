package io.taskmigo.authorization.role.internal;

import io.taskmigo.authorization.core.AuthorizationName;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.RoleHierarchy;
import io.taskmigo.authorization.role.RoleHierarchyException;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.role.internal.RoleStore.RoleState;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Implements Role use cases independently from the JPA adapter.
@Service
class DefaultRoleService implements RoleService {

    private final RoleStore roles;

    DefaultRoleService(RoleStore roles) {
        this.roles = roles;
    }

    @Override
    @Transactional
    public UUID createRole(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        @Nullable Collection<UUID> childRoleIds
    ) {
        UUID id = UUID.randomUUID();
        Set<UUID> requestedChildIds = childRoleIds == null ? Set.of() : Set.copyOf(childRoleIds);
        List<RoleState> allRoles = new ArrayList<>(this.roles.loadAllForUpdate());
        requireChildren(requestedChildIds, allRoles);

        RoleHierarchy hierarchy;
        try {
            hierarchy = hierarchy(allRoles).replacingChildren(id, requestedChildIds);
        } catch (RoleHierarchyException exception) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, hierarchyFailureMessage(exception));
        }

        RoleState role = new RoleState(
            id,
            AuthorizationName.requiredRole(code, "code"),
            AuthorizationName.requiredDisplayName(displayName, "displayName"),
            description,
            Set.of(),
            requestedChildIds
        );
        this.roles.create(role);
        allRoles.add(role);
        this.roles.replaceClosure(allRoles, hierarchy);
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireRoles(Collection<UUID> ids) {
        Set<UUID> requestedIds = Set.copyOf(ids);
        if (!this.roles.containsAll(requestedIds)) {
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
        List<RoleState> allRoles = new ArrayList<>(this.roles.loadAllForUpdate());
        RoleState parent = state(parentRoleId, allRoles);
        requireChildren(requestedIds, allRoles);

        RoleHierarchy hierarchy;
        try {
            hierarchy = hierarchy(allRoles).replacingChildren(parent.id(), requestedIds);
        } catch (RoleHierarchyException exception) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, hierarchyFailureMessage(exception));
        }

        this.roles.replaceChildren(parent.id(), requestedIds);
        allRoles.replaceAll(role ->
            role.id().equals(parent.id())
                ? new RoleState(
                      role.id(),
                      role.code(),
                      role.displayName(),
                      role.description(),
                      role.statementIds(),
                      requestedIds
                  )
                : role
        );
        this.roles.replaceClosure(allRoles, hierarchy);
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
            .findByIds(this.roles.descendantRoleIds(requestedIds))
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .toList();
    }

    private static RoleState state(UUID id, Collection<RoleState> roles) {
        return roles
            .stream()
            .filter(role -> role.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new RoleException(RoleException.Type.BAD_REQUEST, "Role does not exist"));
    }

    private static void requireChildren(Set<UUID> childIds, Collection<RoleState> roles) {
        long found = roles.stream().map(RoleState::id).filter(childIds::contains).count();
        if (found != childIds.size()) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, "One or more child Roles do not exist");
        }
    }

    private static RoleHierarchy hierarchy(Collection<RoleState> roles) {
        Map<UUID, Set<UUID>> children = roles.stream().collect(Collectors.toMap(RoleState::id, RoleState::childIds));
        return RoleHierarchy.from(children);
    }

    private static String hierarchyFailureMessage(RoleHierarchyException exception) {
        String message = exception.getMessage();
        return message == null ? "Role hierarchy is invalid" : message;
    }
}
