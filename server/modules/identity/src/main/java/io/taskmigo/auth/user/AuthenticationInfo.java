package io.taskmigo.auth.user;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Exposes persisted user identity and credential state to authentication adapters.
public record AuthenticationInfo(
    UUID id,
    String username,
    String displayName,
    boolean active,
    @Nullable String passwordHash
) {}
