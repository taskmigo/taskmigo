package io.taskmigo.identity.user.domain;

import org.jspecify.annotations.Nullable;

/// Represents Identity-owned persisted credential state.
///
/// Managed input is initial-only: once any persisted password hash exists, later managed input cannot replace it.
public record UserCredential(@Nullable String passwordHash) {
    /// Returns an empty credential for a runtime-created User.
    public static UserCredential empty() {
        return new UserCredential(null);
    }

    /// Returns persisted or initially supplied credential state without reinterpreting its encoding.
    public static UserCredential initial(@Nullable String passwordHash) {
        return new UserCredential(passwordHash);
    }

    /// Returns whether a password hash has already been initialized.
    public boolean initialized() {
        return this.passwordHash != null;
    }

    /// Initializes a missing credential from non-blank managed input and otherwise preserves the current credential.
    public UserCredential initializeIfMissing(@Nullable String initialPasswordHash) {
        if (this.initialized() || initialPasswordHash == null || initialPasswordHash.isBlank()) {
            return this;
        }
        return new UserCredential(initialPasswordHash);
    }
}
