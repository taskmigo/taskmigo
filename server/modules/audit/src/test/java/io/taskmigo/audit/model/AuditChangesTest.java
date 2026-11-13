package io.taskmigo.audit.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditChangesTest {

    /**
     * Verifies that unchanged fields are omitted and secret field values are not persisted.
     *
     * Given: one unchanged visible field and one changed password hash.
     * Expect: only a sensitive marker, with no before or after values.
     */
    @Test
    @DisplayName("omits unchanged fields and redacts changed secrets")
    void shouldOnlyIncludeRedactedChangeWhenSensitiveValueChanges() {
        // Arrange
        String before = "old-hash";
        String after = "new-hash";

        // Act
        List<AuditChange> changes = AuditChanges.builder()
            .changed("displayName", "Same", "Same")
            .sensitiveChanged("passwordHash", before, after)
            .build();

        // Assert
        assertThat(changes).containsExactly(AuditChange.sensitive("passwordHash"));
        assertThat(changes.getFirst().before()).isNull();
        assertThat(changes.getFirst().after()).isNull();
    }
}
