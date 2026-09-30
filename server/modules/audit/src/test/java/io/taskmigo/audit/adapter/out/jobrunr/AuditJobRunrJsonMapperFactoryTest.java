package io.taskmigo.audit.adapter.out.jobrunr;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditJobRunrJsonMapperFactoryTest {

    /**
     * Verifies that durable audit payloads round-trip through the JobRunr 8.6.1 mapper.
     *
     * Given: an audit event whose outer and nested values use the JDK immutable list implementations.
     * Expect: JobRunr serializes and deserializes the complete event without changing its value.
     */
    @Test
    @DisplayName("round-trips immutable audit lists through JobRunr")
    void shouldRoundTripAuditEventWhenPayloadUsesImmutableLists() {
        // Arrange
        AuditEvent event = new AuditEvent(
            UUID.randomUUID(),
            "user",
            UUID.randomUUID(),
            new AuditActor(UUID.randomUUID(), "operator"),
            Instant.parse("2026-09-28T00:00:00Z"),
            List.of(
                AuditChange.visible("roleIds", List.of("before"), List.of("after")),
                AuditChange.visible(
                    "statementIds",
                    List.of("before-1", "before-2", "before-3"),
                    List.of("after-1", "after-2", "after-3")
                ),
                AuditChange.visible("enabled", true, false)
            )
        );
        var mapper = AuditJobRunrJsonMapperFactory.create();

        // Act
        String serialized = mapper.serialize(event);
        AuditEvent roundTrip = mapper.deserialize(serialized, AuditEvent.class);

        // Assert
        assertThat(roundTrip).isEqualTo(event);
    }
}
