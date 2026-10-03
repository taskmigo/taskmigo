package io.taskmigo.audit.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

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
     * Verifies that synchronous audit work is appended inside the caller-owned transaction.
     *
     * Given: one audit event and a healthy store.
     * Expect: the store receives exactly one append request for that event.
     */
    @Test
    @DisplayName("appends audit data synchronously")
    void shouldAppendWhenAuditEventIsProvided() {
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
     * Verifies that audit persistence failures remain visible to the owning mutation transaction.
     *
     * Given: the audit store rejects an append.
     * Expect: the original failure is propagated so the caller transaction can roll back.
     */
    @Test
    @DisplayName("propagates synchronous audit persistence failures")
    void shouldPropagateWhenAuditPersistenceFails() {
        // Arrange
        AuditLogStore logs = Mockito.mock(AuditLogStore.class);
        AuditEvent event = event();
        RuntimeException failure = new IllegalStateException("storage unavailable");
        doThrow(failure).when(logs).append(event);
        DefaultAuditAppendService service = new DefaultAuditAppendService(logs, directTransactions());

        // Act + Assert
        assertThatThrownBy(() -> service.append(event)).isSameAs(failure);
    }

    private static AuditEvent event() {
        return new AuditEvent(
            UUID.randomUUID(),
            "user",
            UUID.randomUUID(),
            new AuditActor(UUID.randomUUID(), "actor"),
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
