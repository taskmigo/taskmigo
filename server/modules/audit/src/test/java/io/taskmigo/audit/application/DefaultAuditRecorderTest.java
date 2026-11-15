package io.taskmigo.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.taskmigo.audit.application.append.AuditAppendService;
import io.taskmigo.audit.context.AuditCause;
import io.taskmigo.audit.context.AuditContext;
import io.taskmigo.audit.context.AuditContexts;
import io.taskmigo.audit.context.AuditExecutor;
import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.audit.model.AuditOperation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class DefaultAuditRecorderTest {

    /**
     * Verifies that a missing execution context cannot silently skip the audit event.
     *
     * Given: an enabled recorder and no trusted entry-point actor.
     * Expect: recording fails without evaluating the diff or appending an event.
     */
    @Test
    @DisplayName("rejects recording without an explicit actor")
    void shouldRejectRecordingWhenActorIsMissing() {
        // Arrange
        AuditAppendService append = mock(AuditAppendService.class);
        DefaultAuditRecorder recorder = new DefaultAuditRecorder(append, Clock.systemUTC());

        // Act + Assert
        assertThatThrownBy(() ->
            recorder.record("role", UUID.randomUUID(), AuditOperation.CREATE, () -> {
                throw new AssertionError("Diff must not be evaluated before attribution");
            })
        ).isInstanceOf(IllegalStateException.class);
        verify(append, never()).append(ArgumentMatchers.any());
    }

    /**
     * Verifies that non-UUID resource identifiers are recorded verbatim, not replaced by fabricated UUIDs.
     *
     * Given: a migration-created OAuth Client with a stable textual identifier.
     * Expect: the persisted event keeps its real client ID and explicit CREATE operation.
     */
    @Test
    @DisplayName("records original textual OAuth client ID")
    void shouldRecordOriginalOAuthClientIdentity() {
        // Arrange
        AuditAppendService append = mock(AuditAppendService.class);
        DefaultAuditRecorder recorder = new DefaultAuditRecorder(append, Clock.systemUTC());
        AuditContext context = AuditContext.system(
            "taskmigo-migration",
            AuditExecutor.migration("migration"),
            AuditCause.installation("startup")
        );
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        AuditContexts.run(context, () ->
            recorder.record("oauth-client", "browser-client", AuditOperation.CREATE, () ->
                List.of(AuditChange.sensitive("clientSecret"))
            )
        );

        // Assert
        verify(append).append(events.capture());
        assertThat(events.getValue().entityId()).isEqualTo("browser-client");
        assertThat(events.getValue().operation()).isEqualTo(AuditOperation.CREATE);
        assertThat(events.getValue().actor().type()).isEqualTo(AuditActor.Type.SYSTEM);
        assertThat(events.getValue().causation()).isEqualTo("INSTALLATION:startup");
    }

    /**
     * Verifies that a user-requested worker job remains attributed to the user.
     *
     * Given: a trusted Worker context with the original User actor and job identifier.
     * Expect: the immutable event retains the User actor rather than substituting the Worker.
     */
    @Test
    @DisplayName("records user attribution independently of worker execution")
    void shouldRecordOriginalActorWhenWorkerExecutesJob() {
        // Arrange
        AuditAppendService append = mock(AuditAppendService.class);
        Instant now = Instant.parse("2026-11-11T00:00:00Z");
        DefaultAuditRecorder recorder = new DefaultAuditRecorder(append, Clock.fixed(now, ZoneOffset.UTC));
        AuditActor actor = new AuditActor(UUID.randomUUID(), "alice");
        AuditContext context = new AuditContext(actor, AuditExecutor.worker("worker"), AuditCause.job("123"));
        UUID entityId = UUID.randomUUID();
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        AuditContexts.run(context, () ->
            recorder.record("ticket", entityId, AuditOperation.UPDATE, () ->
                List.of(AuditChange.visible("title", "Before", "After"))
            )
        );

        // Assert
        verify(append).append(events.capture());
        assertThat(events.getValue().actor()).isEqualTo(actor);
        assertThat(events.getValue().executor()).isEqualTo("worker");
        assertThat(events.getValue().causation()).isEqualTo("ASYNC_JOB:123");
        assertThat(events.getValue().operation()).isEqualTo(AuditOperation.UPDATE);
        assertThat(events.getValue().occurredAt()).isEqualTo(now);
        assertThat(AuditContexts.isBound()).isFalse();
    }
}
