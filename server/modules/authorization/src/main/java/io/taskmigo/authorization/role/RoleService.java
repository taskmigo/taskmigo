package io.taskmigo.authorization.role;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines the application contract for Role use cases.
public interface RoleService {
    UUID createRole(@Nullable String name, @Nullable String description, @Nullable Collection<UUID> childRoleIds);
    void requireRoles(Collection<UUID> ids);
    OffsetPage<RoleInfo> listRoles(
        int page,
        int perPage,
        QueryPredicate<RoleInfo> filter,
        ObjectAuthorizationPredicate<RoleInfo> authorization
    );
    void setChildRoles(UUID parentRoleId, Collection<UUID> childRoleIds);
    List<RoleInfo> descendantRoles(UUID roleId);
    List<RoleInfo> effectiveRoles(Collection<UUID> roleIds);
}
