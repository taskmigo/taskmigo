package io.taskmigo.identity.user.domain;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Owns canonical User identity, profile, status, credential, and reserved-system invariants.
public final class User {

    private final UUID id;
    private final Username username;
    private UserProfile profile;
    private final UserStatus status;
    private UserCredential credential;

    private User(UUID id, Username username, UserProfile profile, UserStatus status, UserCredential credential) {
        this.id = Objects.requireNonNull(id);
        this.username = Objects.requireNonNull(username);
        this.profile = Objects.requireNonNull(profile);
        this.status = Objects.requireNonNull(status);
        this.credential = Objects.requireNonNull(credential);
    }

    /// Creates an ordinary runtime User, rejecting the reserved system username.
    public static User register(
        UUID id,
        @Nullable String username,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    ) {
        Username normalizedUsername = Username.of(username);
        if (normalizedUsername.system()) {
            throw UserRuleViolation.reservedSystemUsername();
        }
        return new User(
            id,
            normalizedUsername,
            UserProfile.of(emails, firstName, lastName),
            UserStatus.ACTIVE,
            UserCredential.empty()
        );
    }

    /// Creates a managed User while enforcing the system User's initial-credential requirement.
    public static User managed(
        UUID id,
        @Nullable String username,
        @Nullable String initialPasswordHash,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    ) {
        Username normalizedUsername = Username.of(username);
        UserProfile profile = UserProfile.of(emails, firstName, lastName);
        if (normalizedUsername.system() && (initialPasswordHash == null || initialPasswordHash.isBlank())) {
            throw UserRuleViolation.systemInitialPasswordRequired();
        }
        return new User(
            id,
            normalizedUsername,
            profile,
            UserStatus.ACTIVE,
            UserCredential.initial(initialPasswordHash)
        );
    }

    /// Reconstitutes persisted canonical User state without exposing persistence representation to application code.
    public static User restore(
        UUID id,
        String username,
        Set<String> emails,
        String firstName,
        String lastName,
        UserStatus status,
        @Nullable String passwordHash
    ) {
        return new User(
            id,
            Username.of(username),
            UserProfile.of(emails, firstName, lastName),
            status,
            UserCredential.initial(passwordHash)
        );
    }

    public UUID id() {
        return this.id;
    }

    public Username username() {
        return this.username;
    }

    public UserProfile profile() {
        return this.profile;
    }

    public UserStatus status() {
        return this.status;
    }

    public UserCredential credential() {
        return this.credential;
    }

    /// Reconciles normalized profile state and reports whether canonical User state changed.
    public boolean reconcileProfile(
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    ) {
        UserProfile requested = UserProfile.of(emails, firstName, lastName);
        if (this.profile.equals(requested)) {
            return false;
        }
        this.profile = requested;
        return true;
    }

    /// Initializes a previously missing credential without overwriting any persisted hash.
    public boolean initializeCredential(@Nullable String initialPasswordHash) {
        UserCredential requested = this.credential.initializeIfMissing(initialPasswordHash);
        if (this.credential.equals(requested)) {
            return false;
        }
        this.credential = requested;
        return true;
    }

    /// Enforces the managed-lifecycle restriction that the system User cannot be removed.
    public void requireManagedDeletionAllowed() {
        if (this.username.system()) {
            throw UserRuleViolation.systemUserDeletionForbidden();
        }
    }
}
