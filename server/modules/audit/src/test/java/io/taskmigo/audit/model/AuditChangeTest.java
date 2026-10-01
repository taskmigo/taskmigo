package io.taskmigo.audit.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditChangeTest {

    /**
     * Verifies that a sensitive audit change cannot retain either side of the field diff.
     *
     * Given: a sensitive passwordHash change that still contains a previous value.
     * Expect: construction is rejected before the change can reach audit persistence.
     */
    @Test
    @DisplayName("rejects sensitive audit changes that still contain values")
    void shouldRejectValuesWhenChangeIsSensitive() {
        // Arrange
        String secret = "secret";

        // Act + Assert
        assertThatThrownBy(() -> new AuditChange("passwordHash", secret, null, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Sensitive audit changes");
    }
}
