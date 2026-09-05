package io.taskmigo.auth.role;

import io.taskmigo.auth.authorization.AuthorizationName;
import io.taskmigo.auth.authorization.HierarchyClosureWriter;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationService;
import io.taskmigo.foundation.OffsetPage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages global roles, statement assignments, and role hierarchy.
@Service
public class RoleService {

    private final RoleRepository roles;
    private final HierarchyClosureWriter closureWriter;
    private final ObjectAuthorizationService objectAuthorization;

    RoleService(
        RoleRepository roles,
        HierarchyClosureWriter closureWriter,
        ObjectAuthorizationService objectAuthorization
    ) {
        this.roles = roles;
        this.closureWriter = closureWriter;
        this.objectAuthorization = objectAuthorization;
    }

    /// Creates a Role and its direct child-Role relationships as one atomic operation.
    ///
    /// Duplicate child ids are normalized. Creation fails without persisting the Role when a child does not exist or
    /// would make the Role graph cyclic.
    ///
    /// @param name the display name of the Role
    /// @param description the optional explanation of the Role's purpose
    /// @param childRoleIds the optional direct child Roles inherited by the new Role
    /// @return the id of the created Role
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
        RoleHierarchy.from(allRoles).replacingChildren(id, requestedChildIds);

        RoleEntity role = new RoleEntity(id, AuthorizationName.requiredRole(name, "name"), description);
        role.childRoles.addAll(children);
        this.roles.save(role);
        allRoles.add(role);
        this.roles.flush();
        this.refreshClosure(allRoles);
        return id;
    }

    @Transactional(readOnly = true)
    public void requireRoles(Collection<UUID> ids) {
        List<RoleEntity> found = this.roles.findAllByIdIn(ids);
        if (found.size() != ids.size()) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, "One or more Roles do not exist");
        }
    }

    /// Resolves a Role name for bootstrap references, including persisted definitions from prior runs.
    @Transactional(readOnly = true)
    public UUID requireRoleByName(String name) {
        return this.roles
            .findByName(AuthorizationName.requiredRole(name, "role reference"))
            .map(entity -> entity.id)
            .orElseThrow(() ->
                new IllegalStateException("Built-in authorization Role reference does not exist: " + name)
            );
    }

    /// Lists global Roles with a stable id order for offset pagination.
    ///
    /// @param page the one-based page requested by the API client
    /// @param perPage the maximum number of Roles to return
    /// @return one page of global Roles
    @Transactional(readOnly = true)
    public OffsetPage<RoleInfo> listRoles(int page, int perPage) {
        return this.listRoles(page, perPage, null);
    }

    /// Lists Roles using an optional database-side object authorization predicate.
    @Transactional(readOnly = true)
    public OffsetPage<RoleInfo> listRoles(
        int page,
        int perPage,
        ObjectAuthorizationService.@Nullable ObjectAuthorizationPlan authorization
    ) {
        if (authorization != null && authorization.deniesAll()) {
            return new OffsetPage<>(List.of(), 0, 0);
        }
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var roles =
            authorization == null
                ? this.roles.findAllBy(pageable)
                : this.roles.findAll(this.objectAuthorization.specification(authorization), pageable);
        return new OffsetPage<>(
            roles.map(RoleService::info).getContent(),
            roles.getTotalElements(),
            roles.getTotalPages()
        );
    }

    /// Replaces a Role's direct children after validating the resulting global graph.
    ///
    /// Duplicate child ids are normalized. The replacement is rejected before persistence when a child does not
    /// exist or would make the Role graph cyclic. Concurrent hierarchy writers are serialized before validation.
    ///
    /// @param parentRoleId the Role whose outgoing hierarchy edges are replaced
    /// @param childRoleIds the complete desired set of direct child Roles
    @Transactional
    public void setChildRoles(UUID parentRoleId, Collection<UUID> childRoleIds) {
        Set<UUID> requestedIds = Set.copyOf(childRoleIds);
        List<RoleEntity> allRoles = new ArrayList<>(this.roles.findAllForUpdate());
        RoleEntity parent = entity(parentRoleId, allRoles);
        List<RoleEntity> children = requireChildRoles(requestedIds, allRoles);

        RoleHierarchy.from(allRoles).replacingChildren(parent.id, requestedIds);
        parent.childRoles.clear();
        parent.childRoles.addAll(children);
        this.roles.flush();
        this.refreshClosure(allRoles);
    }

    /// Resolves every transitive descendant of a Role once, in deterministic id order.
    ///
    /// Traversal terminates even if legacy or externally corrupted persistence contains a cycle.
    ///
    /// @param roleId the Role at the root of the downward traversal
    /// @return all descendants, excluding the root Role
    @Transactional(readOnly = true)
    public List<RoleInfo> descendantRoles(UUID roleId) {
        this.requireEntity(roleId);
        return this.effectiveRoles(Set.of(roleId))
            .stream()
            .filter(role -> !role.id().equals(roleId))
            .toList();
    }

    /// Resolves the supplied Roles and every Role inherited from them exactly once.
    ///
    /// The result is ordered by Role id and traversal terminates if persisted data is cyclic.
    ///
    /// @param roleIds the directly included Roles
    /// @return the direct and inherited Roles in deterministic order
    /// @throws RoleException if any supplied Role does not exist
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
            .sorted((left, right) -> left.id.compareTo(right.id))
            .map(RoleService::info)
            .toList();
    }

    private void requireEntity(UUID id) {
        this.roles
            .findById(id)
            .orElseThrow(() -> new RoleException(RoleException.Type.BAD_REQUEST, "Role does not exist"));
    }

    private static RoleEntity entity(UUID id, Collection<RoleEntity> roles) {
        return roles
            .stream()
            .filter(role -> role.id.equals(id))
            .findFirst()
            .orElseThrow(() -> new RoleException(RoleException.Type.BAD_REQUEST, "Role does not exist"));
    }

    private static List<RoleEntity> requireChildRoles(Set<UUID> childRoleIds, Collection<RoleEntity> roles) {
        List<RoleEntity> children = roles
            .stream()
            .filter(role -> childRoleIds.contains(role.id))
            .toList();
        if (children.size() != childRoleIds.size()) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, "One or more child Roles do not exist");
        }
        return children;
    }

    private void refreshClosure(Collection<RoleEntity> allRoles) {
        RoleHierarchy hierarchy = RoleHierarchy.from(allRoles);
        this.closureWriter.replace(
            allRoles,
            RoleEntity::id,
            roleId -> hierarchy.reachableFrom(Set.of(roleId)),
            RoleHierarchyClosureEntity::new,
            RoleHierarchyClosureEntity.class
        );
    }

    private static RoleInfo info(RoleEntity role) {
        return info(role, Set.of());
    }

    private static RoleInfo info(RoleEntity role, Set<UUID> ancestors) {
        if (ancestors.contains(role.id)) {
            return new RoleInfo(role.id, role.name, role.description, List.of());
        }
        Set<UUID> nextAncestors = new HashSet<>(ancestors);
        nextAncestors.add(role.id);
        List<RoleInfo> children = role.childRoles
            .stream()
            .sorted((left, right) -> left.id.compareTo(right.id))
            .map(child -> info(child, nextAncestors))
            .toList();
        return new RoleInfo(role.id, role.name, role.description, children);
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }
}
