package io.taskmigo.migration.application.port.in;

import io.taskmigo.migration.application.model.InstallationPlan;

/// Reconciles one complete desired installation state.
public interface InstallationService {
    /// Validates and reconciles the desired installation state atomically.
    ///
    /// @param plan desired installation state
    void install(InstallationPlan plan);
}
