package io.taskmigo.auth.user;

import java.util.Set;
import java.util.UUID;

/// Exposes stable user identity and profile data to application consumers.
public record UserInfo(
    UUID id,
    String username,
    String firstName,
    String lastName,
    Set<String> emails,
    String displayName
) {}
