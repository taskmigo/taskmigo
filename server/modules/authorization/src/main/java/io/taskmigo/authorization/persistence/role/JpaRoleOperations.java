package io.taskmigo.authorization.persistence.role;

import io.taskmigo.authorization.core.AuthorizationName;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.persistence.HierarchyClosureWriter;
import io.taskmigo.authorization.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.RoleHierarchy;
import io.taskmigo.authorization.role.RoleHierarchyException;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages Access Control Roles and their hierarchy.
@Service
public class JpaRoleOperations implements RoleService {

    private final RoleRepository roles;
    private final HierarchyClosureWriter closureWriter;
    private final QueryPredicateBinder<RoleInfo, RoleEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<RoleInfo, RoleEntity> objectBinder;

    public JpaRoleOperations(
        RoleRepository roles,
        HierarchyClosureWriter closureWriter,
        QueryPredicateBinder<RoleInfo, RoleEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<RoleInfo, RoleEntity> objectBinder
    ) {
        this.roles = roles;
        this.closureWriter = closureWriter;
        this.queryBinder = queryBinder;
        this.objectBinder = objectBinder;
    }

    /// Creates a Role and its direct child-Role relationships as one atomic operation.
    @Transactional
    public UUID createRole(
        @Nullable String name,
        @Nullable String description,
        @Nullable Collection<UUID> childRoleIds
    ) {
        UUID id = UUID.randomUUID();
        Set<UUID> requestedChildIds = childRoleIds == null ? Set.of() : Set.copyOf(childRoleIds);
        List<RoleEntity> allRoles = new ArrayList<>(this.roles.findAllForUpdate());
        List<RoleEntity> children = requireChildRoles(requestedChildIds, allRoles);
        try {
            hierarchy(allRoles).replacingChildren(id, requestedChildIds);
        } catch (RoleHierarchyException exception) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, hierarchyFailureMessage(exception));
        }

        RoleEntity role = new RoleEntity(id, AuthorizationName.requiredRole(name, "name"), description);
        role.addChildRoles(children);
        this.roles.save(role);
        allRoles.add(role);
        this.roles.flush();
        this.refreshClosure(allRoles);
        return id;
    }

    /// Verifies that every supplied Role exists.
    @Transactional(readOnly = true)
    public void requireRoles(Collection<UUID> ids) {
        Set<UUID> requestedIds = Set.copyOf(ids);
        List<RoleEntity> found = this.roles.findAllByIdIn(requestedIds);
        if (found.size() != requestedIds.size()) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, "One or more Roles do not exist");
        }
    }

    /// Resolves a Role name for bootstrap references, including persisted definitions from prior runs.
    @Transactional(readOnly = true)
    public UUID requireRoleByName(String name) {
        return this.roles
            .findByName(AuthorizationName.requiredRole(name, "role reference"))
            .map(RoleEntity::id)
            .orElseThrow(() ->
                new IllegalStateException("Built-in authorization Role reference does not exist: " + name)
            );
    }

    /// Lists Roles by binding both opaque predicates before pagination.
    @Transactional(readOnly = true)
    public OffsetPage<RoleInfo> listRoles(
        int page,
        int perPage,
        QueryPredicate<RoleInfo> filter,
        ObjectAuthorizationPredicate<RoleInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var roles = this.roles.findAll(
            this.queryBinder.bind(filter).and(this.objectBinder.bind(authorization)),
            pageable
        );
        return new OffsetPage<>(
            roles.map(JpaRoleOperations::info).getContent(),
            roles.getTotalElements(),
            roles.getTotalPages()
        );
    }

    /// Replaces a Role's direct children after validating the resulting global graph.
    @Transactional
    public void setChildRoles(UUID parentRoleId, Collection<UUID> childRoleIds) {
        Set<UUID> requestedIds = Set.copyOf(childRoleIds);
        List<RoleEntity> allRoles = new ArrayList<>(this.roles.findAllForUpdate());
        RoleEntity parent = entity(parentRoleId, allRoles);
        List<RoleEntity> children = requireChildRoles(requestedIds, allRoles);

        try {
            hierarchy(allRoles).replacingChildren(parent.id(), requestedIds);
        } catch (RoleHierarchyException exception) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, hierarchyFailureMessage(exception));
        }
        parent.replaceChildRoles(children);
        this.roles.flush();
        this.refreshClosure(allRoles);
    }

    /// Resolves every transitive descendant of a Role once, in deterministic id order.
    @Transactional(readOnly = true)
    public List<RoleInfo> descendantRoles(UUID roleId) {
        this.requireEntity(roleId);
        return this.effectiveRoles(Set.of(roleId))
            .stream()
            .filter(role -> !role.id().equals(roleId))
            .toList();
    }

    /// Resolves the supplied Roles and every Role inherited from them exactly once.
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRoles(Collection<UUID> roleIds) {
        Set<UUID> requestedIds = Set.copyOf(roleIds);
        if (requestedIds.isEmpty()) {
            return List.of();
        }

        if (this.roles.findAllByIdIn(requestedIds).size() != requestedIds.size()) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, "One or more Roles do not exist");
        }

        return this.roles
            .findDistinctByIdIn(this.roles.findDescendantRoleIds(requestedIds))
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .map(JpaRoleOperations::info)
            .toList();
    }

    void requireEntity(UUID id) {
        this.roles
            .findById(id)
            .orElseThrow(() -> new RoleException(RoleException.Type.BAD_REQUEST, "Role does not exist"));
    }

    private static RoleEntity entity(UUID id, Collection<RoleEntity> roles) {
        return roles
            .stream()
            .filter(role -> role.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new RoleException(RoleException.Type.BAD_REQUEST, "Role does not exist"));
    }

    private static List<RoleEntity> requireChildRoles(Set<UUID> childRoleIds, Collection<RoleEntity> roles) {
        List<RoleEntity> children = roles
            .stream()
            .filter(role -> childRoleIds.contains(role.id()))
            .toList();
        if (children.size() != childRoleIds.size()) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, "One or more child Roles do not exist");
        }
        return children;
    }

    private void refreshClosure(Collection<RoleEntity> allRoles) {
        RoleHierarchy hierarchy = hierarchy(allRoles);
        this.closureWriter.replace(
            allRoles,
            RoleEntity::id,
            roleId -> hierarchy.reachableFrom(Set.of(roleId)),
            RoleHierarchyClosureEntity::new,
            RoleHierarchyClosureEntity.class
        );
    }

    private static RoleHierarchy hierarchy(Collection<RoleEntity> roles) {
        return RoleHierarchy.from(
            roles
                .stream()
                .collect(
                    Collectors.toMap(RoleEntity::id, role ->
                        role.childRoles().stream().map(RoleEntity::id).collect(Collectors.toSet())
                    )
                )
        );
    }

    private static String hierarchyFailureMessage(RoleHierarchyException exception) {
        String message = exception.getMessage();
        return message == null ? "Role hierarchy is invalid" : message;
    }

    private static RoleInfo info(RoleEntity role) {
        return info(role, Set.of());
    }

    private static RoleInfo info(RoleEntity role, Set<UUID> ancestors) {
        if (ancestors.contains(role.id())) {
            return new RoleInfo(role.id(), role.name(), role.description(), List.of());
        }
        Set<UUID> nextAncestors = new HashSet<>(ancestors);
        nextAncestors.add(role.id());
        List<RoleInfo> children = role
            .childRoles()
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .map(child -> info(child, nextAncestors))
            .toList();
        return new RoleInfo(role.id(), role.name(), role.description(), children);
    }
}
