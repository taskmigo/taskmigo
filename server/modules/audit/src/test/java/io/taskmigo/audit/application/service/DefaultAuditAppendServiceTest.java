package io.taskmigo.audit.application.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.audit.application.port.out.AuditLogAppendRepository;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditFieldChange;
import io.taskmigo.audit.model.AuditMutationEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class DefaultAuditAppendServiceTest {

    /**
     * Verifies redelivery after a committed append has no second observable effect.
     *
     * Given: an event identifier already present in audit persistence.
     * Expect: the appender returns without inserting another row.
     */
    @Test
    @DisplayName("does not append duplicate source events")
    void shouldNotAppendWhenSourceEventAlreadyExists() {
        // Arrange
        AuditLogAppendRepository logs = mock(AuditLogAppendRepository.class);
        UUID eventId = UUID.randomUUID();
        when(logs.existsBySourceEventId(eventId)).thenReturn(true);
        var service = new DefaultAuditAppendService(logs);
        AuditMutationEvent event = AuditMutationEvent.user(
            eventId,
            UUID.randomUUID(),
            new AuditActor(UUID.randomUUID(), "operator"),
            Instant.now(),
            List.of(AuditFieldChange.visible("firstName", "Old", "New"))
        );

        // Act
        service.append(event);

        // Assert
        verify(logs, never()).append(ArgumentMatchers.any());
    }
}
