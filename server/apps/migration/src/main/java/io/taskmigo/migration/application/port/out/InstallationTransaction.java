package io.taskmigo.migration.application.port.out;

import java.util.function.Supplier;

/// Executes installation work with the required transaction semantics.
public interface InstallationTransaction {

    /// Executes one SERIALIZABLE unit and retries framework-classified transient conflicts up to the requested bound.
    ///
    /// @param maxAttempts maximum number of attempts, including the initial attempt
    /// @param work work that creates a fresh result for each attempt
    /// @return result of the successful attempt
    /// @param <T> result type
    <T> T serializable(int maxAttempts, Supplier<T> work);
}
