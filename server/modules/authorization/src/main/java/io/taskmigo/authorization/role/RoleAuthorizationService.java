package io.taskmigo.authorization.role;

import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines the application contract for Role-to-Statement assignment use cases.
public interface RoleAuthorizationService {
    UUID reconcile(@Nullable String name, @Nullable String description, Collection<UUID> statementIds);
    void setStatements(UUID roleId, Collection<UUID> statementIds);
}
