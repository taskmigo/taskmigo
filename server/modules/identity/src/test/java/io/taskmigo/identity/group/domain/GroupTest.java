package io.taskmigo.identity.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupTest {

    /**
     * Verifies canonical Group identity and profile normalization.
     *
     * Given: padded code and display-name values with an unchanged description.
     * Expect: code/display name are trimmed while the optional description is preserved verbatim.
     */
    @Test
    @DisplayName("normalizes stable code and mutable profile state")
    void shouldNormalizeGroupWhenCreatedFromRuntimeInput() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        Group group = Group.create(id, "  engineering ", " Engineering ", "  description  ");

        // Assert
        assertThat(group.id()).isEqualTo(id);
        assertThat(group.code().value()).isEqualTo("engineering");
        assertThat(group.profile().displayName()).isEqualTo("Engineering");
        assertThat(group.profile().description()).isEqualTo("  description  ");
    }

    /**
     * Verifies stable Group identity is not rewritten during managed reconciliation.
     *
     * Given: an existing Group restored with code engineering and a changed display name.
     * Expect: profile changes while the stable code remains engineering.
     */
    @Test
    @DisplayName("preserves stable code when profile is reconciled")
    void shouldPreserveCodeWhenManagedProfileChanges() {
        // Arrange
        Group group = Group.restore(UUID.randomUUID(), "engineering", "Engineering", null);

        // Act
        boolean changed = group.reconcileProfile(" Platform Engineering ", "managed");

        // Assert
        assertThat(changed).isTrue();
        assertThat(group.code().value()).isEqualTo("engineering");
        assertThat(group.profile().displayName()).isEqualTo("Platform Engineering");
        assertThat(group.profile().description()).isEqualTo("managed");
    }

    /**
     * Verifies required Group identity/profile fields are owned by the domain.
     *
     * Given: blank code or display-name input.
     * Expect: the canonical Group rule violation identifies the missing field.
     */
    @Test
    @DisplayName("rejects blank required Group fields")
    void shouldRejectCreationWhenRequiredFieldIsBlank() {
        // Act + Assert
        assertThatThrownBy(() -> Group.create(UUID.randomUUID(), " ", "Engineering", null)).isInstanceOfSatisfying(
            GroupRuleViolation.class,
            exception -> assertThat(exception.detail()).isEqualTo("code is required")
        );
        assertThatThrownBy(() -> Group.create(UUID.randomUUID(), "engineering", " ", null)).isInstanceOfSatisfying(
            GroupRuleViolation.class,
            exception -> assertThat(exception.detail()).isEqualTo("displayName is required")
        );
    }
}
