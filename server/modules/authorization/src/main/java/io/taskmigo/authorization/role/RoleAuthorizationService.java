package io.taskmigo.authorization.role;

import java.util.Collection;
import java.util.UUID;

/// Defines the application contract for Role-to-Statement assignment use cases.
public interface RoleAuthorizationService {
    void setStatements(UUID roleId, Collection<UUID> statementIds);
}
