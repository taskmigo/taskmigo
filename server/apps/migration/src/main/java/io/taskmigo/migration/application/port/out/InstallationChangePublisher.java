package io.taskmigo.migration.application.port.out;

import io.taskmigo.migration.application.model.InstallationChange;
import java.util.List;

/// Publishes installation changes only after a successful transaction.
public interface InstallationChangePublisher {

    /// Publishes committed installation changes.
    ///
    /// @param changes committed changes
    void publish(List<InstallationChange> changes);
}
