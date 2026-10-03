package io.taskmigo.identity.user.application.port.in.api;

import java.util.UUID;

/// Serializes User-owned authentication writes against deletion of the same persisted User.
public interface UserSessionLifecycleService {
    /// Runs a persistence callback while holding the ACTIVE User's row lock in a write transaction.
    ///
    /// @return whether the User was active and the callback ran
    boolean runIfActive(UUID userId, Runnable write);
}
