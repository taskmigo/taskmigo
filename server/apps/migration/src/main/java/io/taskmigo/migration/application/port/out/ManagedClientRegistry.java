package io.taskmigo.migration.application.port.out;

import io.taskmigo.migration.application.model.ManagedOAuthClient;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/// Persists migration-managed OAuth client state behind a framework-neutral application port.
public interface ManagedClientRegistry {

    /// Finds the minimum existing state required for ownership and secret-rotation decisions.
    ///
    /// @param clientId OAuth client identifier
    /// @return existing client state when present
    Optional<ExistingClient> findByClientId(String clientId);

    /// Checks whether persistence already represents the complete desired managed state.
    ///
    /// @param desired desired managed client state
    /// @return whether the persisted representation is equivalent
    boolean matches(ManagedOAuthClient desired);

    /// Persists the complete desired managed client state.
    ///
    /// @param desired desired managed client state
    void save(ManagedOAuthClient desired);

    /// Existing client information required by application reconciliation.
    record ExistingClient(String id, @Nullable String encodedSecret, boolean managed) {}
}
