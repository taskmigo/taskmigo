package io.taskmigo.identity.user.application;

import java.util.UUID;

/// Removes persisted HTTP sessions and OAuth state owned by one stable User identity.
public interface UserSessionStore {
    boolean revoke(UUID userId, String username);
}
