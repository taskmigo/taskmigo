package io.taskmigo.audit.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditEventTest {

    /**
     * Verifies that mutation timestamps use exactly millisecond precision.
     *
     * Given: an Instant containing sub-millisecond precision.
     * Expect: the audit event truncates the timestamp to the containing millisecond.
     */
    @Test
    @DisplayName("normalizes occurredAt to millisecond precision")
    void shouldTruncateOccurredAtWhenSubMillisecondPrecisionIsProvided() {
        // Arrange
        Instant occurredAt = Instant.parse("2026-10-01T12:34:56.123456789Z");

        // Act
        AuditEvent event = new AuditEvent(
            UUID.randomUUID(),
            "user",
            UUID.randomUUID(),
            new AuditActor(UUID.randomUUID(), "actor"),
            occurredAt,
            List.of(AuditChange.visible("firstName", "Before", "After"))
        );

        // Assert
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-01T12:34:56.123Z"));
    }
}
