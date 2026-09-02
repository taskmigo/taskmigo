package io.taskmigo.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.taskmigo.foundation.OffsetPage;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages global roles and their permission grants.
@Service
public class AccessService {

    private final RoleRepository roles;
    private final ObjectAuthorizationService objectAuthorization;

    AccessService(RoleRepository roles, ObjectAuthorizationService objectAuthorization) {
        this.roles = roles;
        this.objectAuthorization = objectAuthorization;
    }

    @Transactional
    public UUID createRole(@Nullable String name, @Nullable String description, @Nullable Set<String> permissions) {
        return this.createRole(name, description, permissions, Set.of());
    }

    /// Creates a Role and its direct child-Role relationships as one atomic operation.
    ///
    /// Duplicate child ids are normalized. Creation fails without persisting the Role when a child does not exist or
    /// would make the Role graph cyclic.
    ///
    /// @param name the display name of the Role
    /// @param description the optional explanation of the Role's purpose
    /// @param permissions the optional direct permission grants
    /// @param childRoleIds the optional direct child Roles inherited by the new Role
    /// @return the id of the created Role
    @Transactional
    public UUID createRole(
        @Nullable String name,
        @Nullable String description,
        @Nullable Set<String> permissions,
        @Nullable Collection<UUID> childRoleIds
    ) {
        Set<String> requested = permissions == null ? Set.of() : Set.copyOf(permissions);
        if (!PermissionCatalog.ALL.containsAll(requested)) {
            Set<String> unknown = new HashSet<>(requested);
            unknown.removeAll(PermissionCatalog.ALL);
            throw new AccessException(AccessException.Type.BAD_REQUEST, "Unknown permissions: " + unknown);
        }
        UUID id = UUID.randomUUID();
        Set<UUID> requestedChildIds = childRoleIds == null ? Set.of() : Set.copyOf(childRoleIds);
        List<RoleEntity> allRoles = this.roles.findAllForUpdate();
        List<RoleEntity> children = requireChildRoles(requestedChildIds, allRoles);
        RoleHierarchy.from(allRoles).replacingChildren(id, requestedChildIds);

        RoleEntity role = new RoleEntity(id, AuthorizationName.requiredRole(name, "name"), description, requested);
        role.childRoles.addAll(children);
        this.roles.save(role);
        return id;
    }

    /// Reconciles a managed Role by stable name and replaces its direct Statement assignments.
    @Transactional
    public UUID reconcileRole(@Nullable String name, @Nullable String description, Collection<UUID> statementIds) {
        String validName = AuthorizationName.requiredRole(name, "name");
        RoleEntity role = this.roles.findByName(validName).orElse(null);
        UUID id;
        if (role == null) {
            id = this.createRole(validName, description, Set.of());
            role = this.roles.findById(id).orElseThrow();
        } else {
            id = role.id;
            role.description = description;
        }
        role.statementIds.clear();
        role.statementIds.addAll(Set.copyOf(statementIds));
        this.roles.flush();
        return id;
    }

    @Transactional(readOnly = true)
    public void requireRoles(Collection<UUID> ids) {
        List<RoleEntity> found = this.roles.findAllByIdIn(ids);
        if (found.size() != ids.size()) throw new AccessException(
            AccessException.Type.BAD_REQUEST,
            "One or more Roles do not exist"
        );
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
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var roles =
            authorization == null
                ? this.roles.findAllBy(pageable)
                : this.roles.findAll(this.objectAuthorization.specification(authorization), pageable);
        return new OffsetPage<>(
            roles.map(AccessService::info).getContent(),
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
        List<RoleEntity> allRoles = this.roles.findAllForUpdate();
        RoleEntity parent = entity(parentRoleId, allRoles);
        List<RoleEntity> children = requireChildRoles(requestedIds, allRoles);

        RoleHierarchy.from(allRoles).replacingChildren(parent.id, requestedIds);
        parent.childRoles.clear();
        parent.childRoles.addAll(children);
        this.roles.flush();
    }

    /// Replaces the Statements directly assigned to a Role.
    ///
    /// Duplicate ids are normalized. Statement existence is validated by the web orchestration layer before this
    /// owner-module mutation is invoked.
    ///
    /// @param roleId the Role whose direct Statements are replaced
    /// @param statementIds the complete desired set of directly assigned Statements
    @Transactional
    public void setStatements(UUID roleId, Collection<UUID> statementIds) {
        RoleEntity role = this.roles
            .findById(roleId)
            .orElseThrow(() -> new AccessException(AccessException.Type.BAD_REQUEST, "Role does not exist"));
        role.statementIds.clear();
        role.statementIds.addAll(Set.copyOf(statementIds));
        this.roles.flush();
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
            .filter(role -> !role.id.equals(roleId))
            .toList();
    }

    /// Resolves the supplied Roles and every Role inherited from them exactly once.
    ///
    /// The result is ordered by Role id and traversal terminates if persisted data is cyclic.
    ///
    /// @param roleIds the directly included Roles
    /// @return the direct and inherited Roles in deterministic order
    /// @throws AccessException if any supplied Role does not exist
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRoles(Collection<UUID> roleIds) {
        Set<UUID> requestedIds = Set.copyOf(roleIds);
        if (requestedIds.isEmpty()) return List.of();

        List<RoleEntity> allRoles = this.roles.findAll();
        Map<UUID, RoleEntity> rolesById = new HashMap<>();
        for (RoleEntity role : allRoles) rolesById.put(role.id, role);
        if (!rolesById.keySet().containsAll(requestedIds)) throw new AccessException(
            AccessException.Type.BAD_REQUEST,
            "One or more Roles do not exist"
        );

        return RoleHierarchy.from(allRoles)
            .reachableFrom(requestedIds)
            .stream()
            .map(rolesById::get)
            .map(AccessService::info)
            .toList();
    }

    public record RoleInfo(
        UUID id,
        String name,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String description,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<RoleInfo> children
    ) {}

    private void requireEntity(UUID id) {
        this.roles
            .findById(id)
            .orElseThrow(() -> new AccessException(AccessException.Type.BAD_REQUEST, "Role does not exist"));
    }

    private static RoleEntity entity(UUID id, Collection<RoleEntity> roles) {
        return roles
            .stream()
            .filter(role -> role.id.equals(id))
            .findFirst()
            .orElseThrow(() -> new AccessException(AccessException.Type.BAD_REQUEST, "Role does not exist"));
    }

    private static List<RoleEntity> requireChildRoles(Set<UUID> childRoleIds, Collection<RoleEntity> roles) {
        List<RoleEntity> children = roles
            .stream()
            .filter(role -> childRoleIds.contains(role.id))
            .toList();
        if (children.size() != childRoleIds.size()) throw new AccessException(
            AccessException.Type.BAD_REQUEST,
            "One or more child Roles do not exist"
        );
        return children;
    }

    private static RoleInfo info(RoleEntity role) {
        return info(role, Set.of());
    }

    private static RoleInfo info(RoleEntity role, Set<UUID> ancestors) {
        if (ancestors.contains(role.id)) return new RoleInfo(role.id, role.name, role.description, List.of());
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
        if (value == null || value.isBlank()) throw new AccessException(
            AccessException.Type.BAD_REQUEST,
            field + " is required"
        );
        return value.trim();
    }
}
