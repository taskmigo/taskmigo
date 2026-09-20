package io.taskmigo.identity.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    /**
     * Verifies canonical runtime User normalization.
     *
     * Given: padded username/name input and duplicate emails that differ only by case.
     * Expect: the aggregate stores trimmed identity/profile values, one lowercase email, ACTIVE status, and no credential.
     */
    @Test
    @DisplayName("normalizes runtime user state")
    void shouldNormalizeRuntimeUserWhenCreated() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        User user = User.register(
            id,
            "  alice  ",
            List.of("Alice@EXAMPLE.COM", " alice@example.com "),
            "  Alice ",
            " User  "
        );

        // Assert
        assertThat(user.id()).isEqualTo(id);
        assertThat(user.username().value()).isEqualTo("alice");
        assertThat(user.profile().firstName()).isEqualTo("Alice");
        assertThat(user.profile().lastName()).isEqualTo("User");
        assertThat(user.profile().emails()).containsExactly("alice@example.com");
        assertThat(user.profile().displayName()).isEqualTo("Alice User");
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.credential().initialized()).isFalse();
    }

    /**
     * Verifies the reserved system-username runtime invariant.
     *
     * Given: ordinary runtime creation with username system.
     * Expect: creation fails with the reserved-system rule.
     */
    @Test
    @DisplayName("rejects the system username for runtime registration")
    void shouldRejectSystemUsernameWhenRuntimeUserIsCreated() {
        // Act + Assert
        assertThatThrownBy(() ->
            User.register(UUID.randomUUID(), " system ", Set.of(), "System", "User")
        ).isInstanceOfSatisfying(UserRuleViolation.class, exception ->
            assertThat(exception.reason()).isEqualTo(UserRuleViolation.Reason.RESERVED_SYSTEM_USERNAME)
        );
    }

    /**
     * Verifies the system User's managed initial-credential invariant.
     *
     * Given: managed creation for the system username without a usable initial hash.
     * Expect: creation fails with the system-initial-password rule.
     */
    @Test
    @DisplayName("requires an initial credential for a new managed system user")
    void shouldRequireInitialCredentialWhenManagedSystemUserIsCreated() {
        // Act + Assert
        assertThatThrownBy(() ->
            User.managed(UUID.randomUUID(), "system", null, Set.of(), "System", "User")
        ).isInstanceOfSatisfying(UserRuleViolation.class, exception ->
            assertThat(exception.reason()).isEqualTo(UserRuleViolation.Reason.SYSTEM_INITIAL_PASSWORD_REQUIRED)
        );
    }

    /**
     * Verifies initial-only managed credential behavior.
     *
     * Given: a persisted User that already has a password hash and different managed input.
     * Expect: initialization reports no change and preserves the persisted hash.
     */
    @Test
    @DisplayName("preserves an existing credential during managed initialization")
    void shouldPreserveExistingCredentialWhenManagedInputChanges() {
        // Arrange
        User user = User.restore(
            UUID.randomUUID(),
            "alice",
            Set.of("alice@example.com"),
            "Alice",
            "User",
            UserStatus.ACTIVE,
            "{bcrypt}existing"
        );

        // Act
        boolean changed = user.initializeCredential("{bcrypt}different");

        // Assert
        assertThat(changed).isFalse();
        assertThat(user.credential().passwordHash()).isEqualTo("{bcrypt}existing");
    }

    /**
     * Verifies managed profile reconciliation uses the same normalization rules as runtime creation.
     *
     * Given: an existing User and differently formatted desired profile values.
     * Expect: the aggregate stores normalized desired state and reports the mutation.
     */
    @Test
    @DisplayName("reconciles managed profile through canonical normalization")
    void shouldNormalizeProfileWhenManagedStateChanges() {
        // Arrange
        User user = User.restore(UUID.randomUUID(), "alice", Set.of(), "Old", "User", UserStatus.ACTIVE, null);

        // Act
        boolean changed = user.reconcileProfile(
            List.of("Alice@EXAMPLE.COM", " alice@example.com "),
            " Alice ",
            " User "
        );

        // Assert
        assertThat(changed).isTrue();
        assertThat(user.profile()).isEqualTo(new UserProfile(Set.of("alice@example.com"), "Alice", "User"));
    }

    /**
     * Verifies the system User cannot participate in managed deletion.
     *
     * Given: the persisted system User aggregate.
     * Expect: the aggregate rejects managed deletion with the dedicated rule.
     */
    @Test
    @DisplayName("rejects managed deletion of the system user")
    void shouldRejectManagedDeletionWhenUserIsSystem() {
        // Arrange
        User user = User.restore(
            UUID.randomUUID(),
            "system",
            Set.of(),
            "System",
            "User",
            UserStatus.ACTIVE,
            "{bcrypt}hash"
        );

        // Act + Assert
        assertThatThrownBy(user::requireManagedDeletionAllowed).isInstanceOfSatisfying(
            UserRuleViolation.class,
            exception ->
                assertThat(exception.reason()).isEqualTo(UserRuleViolation.Reason.SYSTEM_USER_DELETION_FORBIDDEN)
        );
    }
}
