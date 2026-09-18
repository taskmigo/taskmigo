package io.taskmigo.authorization.provisioning;

import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines installation/provisioning operations for managed authorization state.
public interface AuthorizationProvisioningService {
    UUID reconcileStatement(
        @Nullable String name,
        @Nullable String description,
        @Nullable String effect,
        @Nullable String scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    );

    UUID reconcileRole(@Nullable String name, @Nullable String description, Collection<UUID> statementIds);

    UUID requireStatement(String name);

    UUID requireRole(String name);
}
