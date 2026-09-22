package io.taskmigo.authorization.provisioning.application.port.in.api;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningResult;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines installation/provisioning operations for managed authorization state.
public interface AuthorizationProvisioningService {
    AuthorizationProvisioningResult<UUID> reconcileStatement(
        @Nullable String code,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    );

    AuthorizationProvisioningResult<UUID> reconcileRole(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> statementIds
    );

    UUID requireStatement(String code);

    UUID requireRole(String code);

    boolean deleteStatement(String code);

    boolean deleteRole(String code);
}
