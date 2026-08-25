package io.taskmigo.access;

import io.taskmigo.organization.OrganizationService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages organization-owned roles and permission grants.
@Service
public class AccessService {

    private final RoleRepository roles;
    private final OrganizationService organizations;

    AccessService(RoleRepository roles, OrganizationService organizations) {
        this.roles = roles;
        this.organizations = organizations;
    }

    @Transactional
    public UUID createRole(
        UUID organizationId,
        @Nullable String name,
        @Nullable String description,
        @Nullable Set<String> permissions
    ) {
        this.organizations.require(organizationId);
        Set<String> requested = permissions == null ? Set.of() : Set.copyOf(permissions);
        if (!PermissionCatalog.ALL.containsAll(requested)) {
            Set<String> unknown = new HashSet<>(requested);
            unknown.removeAll(PermissionCatalog.ALL);
            throw new AccessException(AccessException.Type.BAD_REQUEST, "Unknown permissions: " + unknown);
        }
        UUID id = UUID.randomUUID();
        this.roles.save(new RoleEntity(id, organizationId, required(name, "name"), description, requested));
        return id;
    }

    @Transactional(readOnly = true)
    public List<RoleInfo> requireRoles(Collection<UUID> ids) {
        List<RoleEntity> found = this.roles.findAllByIdIn(ids);
        if (found.size() != ids.size()) throw new AccessException(AccessException.Type.BAD_REQUEST, "One or more Roles do not exist");
        return found.stream().map(role -> new RoleInfo(role.id, role.organizationId, role.name, Set.copyOf(role.permissions))).toList();
    }

    public record RoleInfo(UUID id, UUID organizationId, String name, Set<String> permissions) {}

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) throw new AccessException(AccessException.Type.BAD_REQUEST, field + " is required");
        return value.trim();
    }
}
