package io.taskmigo.identity.provisioning;

import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines installation/provisioning operations for managed Identity state.
public interface IdentityProvisioningService {
    UUID reconcileUser(
        @Nullable String username,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        Collection<UUID> roleIds,
        Collection<UUID> statementIds
    );

    void reconcileSystemUser(@Nullable String initialPasswordHash);
}
