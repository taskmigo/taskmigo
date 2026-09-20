package io.taskmigo.identity.provisioning.application.port.in.api;

import io.taskmigo.identity.provisioning.IdentityProvisioningResult;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines installation/provisioning operations for managed Identity state.
public interface IdentityProvisioningService {
    IdentityProvisioningResult<UUID> reconcileUser(
        @Nullable String username,
        @Nullable String initialPasswordHash,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        Collection<UUID> roleIds,
        Collection<UUID> groupIds
    );

    boolean deleteUser(String username);
}
