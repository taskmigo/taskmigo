package io.taskmigo.identity.user.application.port.out;

/// Removes persisted authorization-server session state owned by one User identity.
public interface UserSessionStore {
    boolean revoke(String username);
}
