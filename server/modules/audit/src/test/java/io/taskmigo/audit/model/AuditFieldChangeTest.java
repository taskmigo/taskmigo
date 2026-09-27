package io.taskmigo.audit.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditFieldChangeTest {

    /**
     * Verifies sensitive values cannot cross the audit event boundary.
     *
     * Given: a sensitive field change carrying a previous value.
     * Expect: construction fails before the event can be durably published.
     */
    @Test
    @DisplayName("rejects values for sensitive audit fields")
    void shouldRejectValuesWhenSensitiveFieldIsConstructed() {
        // Act + Assert
        assertThatThrownBy(() -> new AuditFieldChange("password", "old-hash", null, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Sensitive audit changes must not contain values");
    }
}
