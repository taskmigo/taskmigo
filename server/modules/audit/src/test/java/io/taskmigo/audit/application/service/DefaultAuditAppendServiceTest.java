package io.taskmigo.audit.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.audit.application.port.out.AuditLogStore;
import io.taskmigo.audit.application.port.out.AuditTransactionRunner;
import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultAuditAppendServiceTest {

    /**
     * Verifies that a new delivery is appended inside the worker transaction.
     *
     * Given: one delivered audit event and a healthy store.
     * Expect: the store receives exactly one append request for that event.
     */
    @Test
    @DisplayName("appends delivered audit work in the worker transaction")
    void shouldAppendWhenAuditEventIsDelivered() {
        // Arrange
        AuditLogStore logs = Mockito.mock(AuditLogStore.class);
        AuditEvent event = event();
        DefaultAuditAppendService service = new DefaultAuditAppendService(logs, directTransactions());

        // Act
        service.append(event);

        // Assert
        verify(logs).append(event);
    }

    /**
     * Verifies that a concurrent unique-key loser is treated as successful redelivery.
     *
     * Given: append fails after another worker has already persisted the same source event.
     * Expect: a fresh existence check observes the row and the append use case completes successfully.
     */
    @Test
    @DisplayName("treats an append failure as success when the source event already exists")
    void shouldTreatAppendFailureAsSuccessWhenSourceEventExists() {
        // Arrange
        AuditLogStore logs = Mockito.mock(AuditLogStore.class);
        AuditEvent event = event();
        doThrow(new IllegalStateException("duplicate")).when(logs).append(event);
        when(logs.existsBySourceEventId(event.id())).thenReturn(true);
        DefaultAuditAppendService service = new DefaultAuditAppendService(logs, directTransactions());

        // Act + Assert
        assertThatCode(() -> service.append(event)).doesNotThrowAnyException();
        verify(logs).existsBySourceEventId(event.id());
    }

    /**
     * Verifies that genuine persistence failures remain visible to JobRunr for retry.
     *
     * Given: append fails and no audit row exists for the source event afterwards.
     * Expect: the original append failure is rethrown instead of being mistaken for redelivery.
     */
    @Test
    @DisplayName("rethrows append failures when the source event was not persisted")
    void shouldRethrowAppendFailureWhenSourceEventDoesNotExist() {
        // Arrange
        AuditLogStore logs = Mockito.mock(AuditLogStore.class);
        AuditEvent event = event();
        RuntimeException failure = new IllegalStateException("storage unavailable");
        doThrow(failure).when(logs).append(event);
        when(logs.existsBySourceEventId(event.id())).thenReturn(false);
        DefaultAuditAppendService service = new DefaultAuditAppendService(logs, directTransactions());

        // Act + Assert
        assertThatThrownBy(() -> service.append(event)).isSameAs(failure);
        verify(logs).existsBySourceEventId(event.id());
    }

    private static AuditEvent event() {
        return new AuditEvent(
            UUID.randomUUID(),
            "user",
            UUID.randomUUID(),
            new AuditActor(UUID.randomUUID(), "operator"),
            Instant.parse("2026-09-28T00:00:00Z"),
            List.of(AuditChange.visible("displayName", "Before", "After"))
        );
    }

    private static AuditTransactionRunner directTransactions() {
        return new AuditTransactionRunner() {
            @Override
            public <T> T read(Supplier<T> work) {
                return work.get();
            }

            @Override
            public void write(Runnable work) {
                work.run();
            }
        };
    }
}
