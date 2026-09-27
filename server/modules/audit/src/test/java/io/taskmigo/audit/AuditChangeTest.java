package io.taskmigo.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuditChangeTest {

    @Test
    void rejectsValuesForSensitiveChanges() {
        assertThatThrownBy(() -> new AuditChange("password", true, "old", "new"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
