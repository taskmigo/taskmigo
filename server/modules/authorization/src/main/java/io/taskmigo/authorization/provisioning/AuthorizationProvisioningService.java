package io.taskmigo.authorization.provisioning;

import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines installation/provisioning operations for managed authorization state.
public interface AuthorizationProvisioningService {
    UUID reconcileStatement(
        @Nullable String code,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    );

    UUID reconcileRole(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> statementIds
    );

    UUID requireStatement(String code);

    UUID requireRole(String code);

    void deleteStatement(String code);

    void deleteRole(String code);
}
