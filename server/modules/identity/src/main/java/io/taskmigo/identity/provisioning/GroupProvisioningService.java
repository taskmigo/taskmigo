package io.taskmigo.identity.provisioning;

import io.taskmigo.foundation.ReconciliationResult;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Reconciles managed Group state without broadening the runtime Group API.
public interface GroupProvisioningService {
    ReconciliationResult<UUID> reconcileGroup(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> roleIds
    );

    boolean deleteGroup(String code);
}
