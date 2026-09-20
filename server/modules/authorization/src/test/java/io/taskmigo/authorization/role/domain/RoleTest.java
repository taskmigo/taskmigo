package io.taskmigo.authorization.role.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoleTest {

    /**
     * Verifies stable Role code identity is preserved while mutable profile and Statement assignment are canonicalized.
     *
     * Given: a valid Role code containing spaces, a padded display name, and duplicate Statement ids.
     * Expect: code is preserved exactly, display name is trimmed, and direct Statement assignment uses set semantics.
     */
    @Test
    @DisplayName("preserves stable code and deduplicates direct statements")
    void shouldPreserveStableCodeAndDeduplicateStatementsWhenRoleIsCreated() {
        // Arrange
        UUID statementId = UUID.randomUUID();

        // Act
        Role role = Role.create(
            UUID.randomUUID(),
            "  admin role  ",
            "  Administrator  ",
            null,
            List.of(statementId, statementId)
        );

        // Assert
        assertThat(role.code().value()).isEqualTo("  admin role  ");
        assertThat(role.profile().displayName()).isEqualTo("Administrator");
        assertThat(role.statementIds()).containsExactly(statementId);
    }

    /**
     * Verifies Role code syntax remains a domain invariant.
     *
     * Given: a Role code shorter than the established minimum length.
     * Expect: aggregate creation fails with the canonical code diagnostic.
     */
    @Test
    @DisplayName("rejects invalid role code")
    void shouldRejectRoleCreationWhenCodeViolatesCanonicalFormat() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act + Assert
        assertThatThrownBy(() -> Role.create(id, "short", "Reader", null, Set.of()))
            .isInstanceOf(RoleRuleViolation.class)
            .hasMessage("code must match [a-zA-Z0-9_ -]{6,255}");
    }

    /**
     * Verifies managed reconciliation owns both mutable Role profile and direct Statement assignment.
     *
     * Given: an existing Role with one profile and Statement set.
     * Expect: reconciling new managed state changes profile and direct Statement assignment without changing code.
     */
    @Test
    @DisplayName("reconciles mutable profile and direct statements")
    void shouldReconcileProfileAndStatementsWhenManagedStateChanges() {
        // Arrange
        UUID oldStatement = UUID.randomUUID();
        UUID newStatement = UUID.randomUUID();
        Role role = Role.restore(UUID.randomUUID(), "reader", "Reader", null, Set.of(oldStatement));

        // Act
        boolean changed = role.reconcile("Updated Reader", "managed", Set.of(newStatement));

        // Assert
        assertThat(changed).isTrue();
        assertThat(role.code().value()).isEqualTo("reader");
        assertThat(role.profile().displayName()).isEqualTo("Updated Reader");
        assertThat(role.statementIds()).containsExactly(newStatement);
    }
}
