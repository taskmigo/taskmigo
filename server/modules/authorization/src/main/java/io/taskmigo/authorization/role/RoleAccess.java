package io.taskmigo.authorization.role;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

/// Exposes authorization-owned Role resolution to higher-level resource modules without leaking persistence details.
@NullMarked
public interface RoleAccess {
    /// Verifies that every supplied Role exists.
    void requireRoles(Collection<UUID> roleIds);

    /// Resolves the supplied Roles and all inherited descendant Roles in deterministic order.
    List<RoleInfo> effectiveRoles(Collection<UUID> roleIds);
}
