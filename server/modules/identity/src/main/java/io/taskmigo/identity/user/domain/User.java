package io.taskmigo.identity.user.domain;

import io.taskmigo.identity.user.UserStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Owns canonical User identity, profile, lifecycle, credential, and reserved-system invariants.
public final class User {

    private final UUID id;
    private @Nullable Username username;
    private @Nullable UserProfile profile;
    private UserStatus status;
    private @Nullable Instant retainedAt;
    private UserCredential credential;

    private User(
        UUID id,
        @Nullable Username username,
        @Nullable UserProfile profile,
        UserStatus status,
        @Nullable Instant retainedAt,
        UserCredential credential
    ) {
        this.id = Objects.requireNonNull(id);
        this.username = username;
        this.profile = profile;
        this.status = Objects.requireNonNull(status);
        this.retainedAt = retainedAt;
        this.credential = Objects.requireNonNull(credential);
        if ((status == UserStatus.RETAINED || status == UserStatus.PURGED) && retainedAt == null) {
            throw new IllegalArgumentException("RETAINED and PURGED users must have retainedAt");
        }
        if (status != UserStatus.RETAINED && status != UserStatus.PURGED && retainedAt != null) {
            throw new IllegalArgumentException("Only RETAINED or PURGED users may have retainedAt");
        }
        if (status == UserStatus.PURGED && (username != null || profile != null || credential.initialized())) {
            throw new IllegalArgumentException("PURGED users cannot retain identity, profile, or credential data");
        }
        if (status != UserStatus.PURGED && (username == null || profile == null)) {
            throw new IllegalArgumentException("Non-PURGED users require identity and profile data");
        }
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
            null,
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
            null,
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
        @Nullable Instant retainedAt,
        @Nullable String passwordHash
    ) {
        return new User(
            id,
            Username.of(username),
            UserProfile.of(emails, firstName, lastName),
            status,
            retainedAt,
            UserCredential.initial(passwordHash)
        );
    }

    /// Reconstitutes pre-retention persisted User state.
    public static User restore(
        UUID id,
        String username,
        Set<String> emails,
        String firstName,
        String lastName,
        UserStatus status,
        @Nullable String passwordHash
    ) {
        return restore(id, username, emails, firstName, lastName, status, null, passwordHash);
    }

    public UUID id() {
        return this.id;
    }

    public Username username() {
        return Objects.requireNonNull(this.username, "PURGED user has no username");
    }

    public UserProfile profile() {
        return Objects.requireNonNull(this.profile, "PURGED user has no profile");
    }

    public UserStatus status() {
        return this.status;
    }

    public @Nullable Instant retainedAt() {
        return this.retainedAt;
    }

    public UserCredential credential() {
        return this.credential;
    }

    /// Reconstitutes a persisted tombstone without recreating identifying data.
    public static User restorePurged(UUID id, Instant retainedAt) {
        return new User(id, null, null, UserStatus.PURGED, retainedAt, UserCredential.empty());
    }

    /// Reconciles normalized profile state and reports whether canonical User state changed.
    public boolean reconcileProfile(
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    ) {
        this.requireMutable();
        UserProfile requested = UserProfile.of(emails, firstName, lastName);
        if (this.profile().equals(requested)) {
            return false;
        }
        this.profile = requested;
        return true;
    }

    /// Initializes a previously missing credential without overwriting any persisted hash.
    public boolean initializeCredential(@Nullable String initialPasswordHash) {
        this.requireMutable();
        UserCredential requested = this.credential.initializeIfMissing(initialPasswordHash);
        if (this.credential.equals(requested)) {
            return false;
        }
        this.credential = requested;
        return true;
    }

    /// Moves an ordinary User into retained read-only state, preserving the first retention timestamp.
    public boolean retain(Instant retainedAt) {
        this.requireManagedDeletionAllowed();
        if (this.status == UserStatus.RETAINED || this.status == UserStatus.PURGED) {
            return false;
        }
        this.status = UserStatus.RETAINED;
        this.retainedAt = Objects.requireNonNull(retainedAt);
        return true;
    }

    /// Permanently removes identifying and credential data while preserving the stable User identifier.
    public boolean purge() {
        if (this.status == UserStatus.PURGED) {
            return false;
        }
        if (this.status != UserStatus.RETAINED) {
            throw UserRuleViolation.retainedUserReadOnly();
        }
        this.username = null;
        this.profile = null;
        this.credential = UserCredential.empty();
        this.status = UserStatus.PURGED;
        return true;
    }

    /// Enforces that lifecycle/profile/access mutations cannot target a retained or purged User.
    public void requireMutable() {
        if (this.status == UserStatus.RETAINED) {
            throw UserRuleViolation.retainedUserReadOnly();
        }
    }

    /// Enforces the managed-lifecycle restriction that the system User cannot be removed.
    public void requireManagedDeletionAllowed() {
        if (this.username != null && this.username.system()) {
            throw UserRuleViolation.systemUserDeletionForbidden();
        }
    }
}
