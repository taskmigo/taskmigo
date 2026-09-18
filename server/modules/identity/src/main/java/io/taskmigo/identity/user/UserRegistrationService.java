package io.taskmigo.identity.user;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Registers a User together with its initial authorization and Group memberships as one application use case.
public interface UserRegistrationService {
    /// Creates the User only after all referenced Roles and Groups are valid, then assigns the requested memberships atomically.
    UUID register(
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable Collection<UUID> roleIds,
        @Nullable Collection<UUID> groupIds
    );
}
