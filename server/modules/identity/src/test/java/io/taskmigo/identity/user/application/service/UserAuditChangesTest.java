package io.taskmigo.identity.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserAuditChangesTest {

    /**
     * Verifies that sensitive User field values are stripped before durable audit publication.
     *
     * Given: previous and new passwordHash values.
     * Expect: the resulting change is marked sensitive and carries neither value.
     */
    @Test
    @DisplayName("removes sensitive values before creating the durable change payload")
    void shouldRemoveValuesWhenFieldIsSensitive() {
        // Arrange
        String before = "{bcrypt}old";
        String after = "{bcrypt}new";

        // Act
        var change = UserAuditChanges.visible("passwordHash", before, after);

        // Assert
        assertThat(change.sensitive()).isTrue();
        assertThat(change.before()).isNull();
        assertThat(change.after()).isNull();
    }
}
