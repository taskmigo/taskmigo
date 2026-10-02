package io.taskmigo.identity.configuration.application.port.in.api;

import io.taskmigo.identity.configuration.ConfigurationSnapshot;
import io.taskmigo.identity.configuration.RetentionDuration;

/// Reads and updates database-backed user-manageable application configuration.
public interface ConfigurationService {
    /// Returns the effective application configuration.
    ConfigurationSnapshot get();

    /// Replaces the User retention duration and returns the effective configuration.
    ConfigurationSnapshot updateUserRetention(RetentionDuration retention);
}
