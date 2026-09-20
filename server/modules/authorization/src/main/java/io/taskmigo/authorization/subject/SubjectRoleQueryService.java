package io.taskmigo.authorization.subject;

import io.taskmigo.authorization.role.RoleInfo;
import java.util.List;
import java.util.UUID;

/// Resolves effective Roles from Access Control grants and Role hierarchy state.
public interface SubjectRoleQueryService {
    /// Returns Roles effective for one opaque subject after expanding transitive subjects.
    List<RoleInfo> effectiveRoles(SubjectRef subject);

    /// Returns Roles effective for an authenticated principal and its transitive subjects.
    List<RoleInfo> effectiveRolesForPrincipal(UUID principalId);
}
