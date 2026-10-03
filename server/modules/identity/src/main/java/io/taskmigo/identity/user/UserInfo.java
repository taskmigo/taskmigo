package io.taskmigo.identity.user;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Exposes stable user identity, profile, and lifecycle data to application consumers.
public record UserInfo(
    UUID id,
    String username,
    String firstName,
    String lastName,
    Set<String> emails,
    String displayName,
    UserStatus status,
    @Nullable Instant retainedAt
) {
    public UserInfo(
        UUID id,
        String username,
        String firstName,
        String lastName,
        Set<String> emails,
        String displayName
    ) {
        this(id, username, firstName, lastName, emails, displayName, UserStatus.ACTIVE, null);
    }
}
