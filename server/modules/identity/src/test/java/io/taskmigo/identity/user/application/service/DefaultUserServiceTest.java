package io.taskmigo.identity.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.application.port.out.UserAuditPublisher;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DefaultUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    /**
     * Verifies that a real User statement mutation publishes the complete immutable audit payload.
     *
     * Given: an existing User whose direct statement assignment changes from one ID to another.
     * Expect: assignment succeeds and one audit event captures entity, actor, mutation time, and deterministic diff.
     */
    @Test
    @DisplayName("publishes a complete audit event when direct statements change")
    void shouldPublishAuditEventWhenDirectStatementsChange() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID beforeId = UUID.randomUUID();
        UUID afterId = UUID.randomUUID();
        UserMutationActor actor = new UserMutationActor(UUID.randomUUID(), "operator");
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        UserAuditPublisher audits = Mockito.mock(UserAuditPublisher.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(users.exists(userId)).thenReturn(true);
        when(queries.statementIds(subject)).thenReturn(Set.of(beforeId));
        DefaultUserService service = service(users, queries, assignments, audits);

        // Act
        service.setStatements(userId, List.of(afterId), actor);

        // Assert
        verify(assignments).setStatements(subject, Set.of(afterId));
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audits).publish(event.capture());
        assertThat(event.getValue().entityType()).isEqualTo("user");
        assertThat(event.getValue().entityId()).isEqualTo(userId);
        assertThat(event.getValue().actor().id()).isEqualTo(actor.id());
        assertThat(event.getValue().actor().username()).isEqualTo(actor.username());
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(event.getValue().changes())
            .singleElement()
            .satisfies(change -> {
                assertThat(change.field()).isEqualTo("statementIds");
                assertThat(change.before()).isEqualTo(List.of(beforeId.toString()));
                assertThat(change.after()).isEqualTo(List.of(afterId.toString()));
                assertThat(change.sensitive()).isFalse();
            });
    }

    /**
     * Verifies that a no-op User statement mutation produces no assignment and no audit event.
     *
     * Given: a User already has exactly the requested direct statement set.
     * Expect: neither the assignment port nor the audit publisher is invoked.
     */
    @Test
    @DisplayName("does not assign or publish when direct statements are unchanged")
    void shouldNotPublishAuditEventWhenDirectStatementsAreUnchanged() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        UserAuditPublisher audits = Mockito.mock(UserAuditPublisher.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(users.exists(userId)).thenReturn(true);
        when(queries.statementIds(subject)).thenReturn(Set.of(statementId));
        DefaultUserService service = service(users, queries, assignments, audits);

        // Act
        service.setStatements(userId, List.of(statementId), new UserMutationActor(UUID.randomUUID(), "operator"));

        // Assert
        verify(assignments, never()).setStatements(any(), any());
        verify(audits, never()).publish(any());
    }

    /**
     * Verifies that a real User Role mutation publishes one audit event with a deterministic Role diff.
     *
     * Given: an existing User whose direct Role assignment changes from one ID to another.
     * Expect: assignment succeeds and the audit event records the Role IDs, actor, and originating timestamp.
     */
    @Test
    @DisplayName("publishes a complete audit event when direct roles change")
    void shouldPublishAuditEventWhenDirectRolesChange() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID beforeId = UUID.randomUUID();
        UUID afterId = UUID.randomUUID();
        UserMutationActor actor = new UserMutationActor(UUID.randomUUID(), "operator");
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        UserAuditPublisher audits = Mockito.mock(UserAuditPublisher.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(users.exists(userId)).thenReturn(true);
        when(queries.roleIds(subject)).thenReturn(Set.of(beforeId));
        DefaultUserService service = service(users, queries, assignments, audits);

        // Act
        service.setRoles(userId, List.of(afterId), actor);

        // Assert
        verify(assignments).setRoles(subject, Set.of(afterId));
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audits).publish(event.capture());
        assertThat(event.getValue().entityType()).isEqualTo("user");
        assertThat(event.getValue().entityId()).isEqualTo(userId);
        assertThat(event.getValue().actor().id()).isEqualTo(actor.id());
        assertThat(event.getValue().actor().username()).isEqualTo(actor.username());
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(event.getValue().changes())
            .singleElement()
            .satisfies(change -> {
                assertThat(change.field()).isEqualTo("roleIds");
                assertThat(change.before()).isEqualTo(List.of(beforeId.toString()));
                assertThat(change.after()).isEqualTo(List.of(afterId.toString()));
                assertThat(change.sensitive()).isFalse();
            });
    }

    /**
     * Verifies that a no-op User Role mutation produces no assignment and no audit event.
     *
     * Given: a User already has exactly the requested direct Role set.
     * Expect: neither the assignment port nor the audit publisher is invoked.
     */
    @Test
    @DisplayName("does not assign or publish when direct roles are unchanged")
    void shouldNotPublishAuditEventWhenDirectRolesAreUnchanged() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        UserAuditPublisher audits = Mockito.mock(UserAuditPublisher.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(users.exists(userId)).thenReturn(true);
        when(queries.roleIds(subject)).thenReturn(Set.of(roleId));
        DefaultUserService service = service(users, queries, assignments, audits);

        // Act
        service.setRoles(userId, List.of(roleId), new UserMutationActor(UUID.randomUUID(), "operator"));

        // Assert
        verify(assignments, never()).setRoles(any(), any());
        verify(audits, never()).publish(any());
    }

    private static DefaultUserService service(
        UserQueryRepository users,
        SubjectGrantQueryService queries,
        SubjectGrantAssignmentService assignments,
        UserAuditPublisher audits
    ) {
        return new DefaultUserService(
            users,
            queries,
            assignments,
            audits,
            directTransactions(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static TransactionRunner directTransactions() {
        return new TransactionRunner() {
            @Override
            public <T> T read(Supplier<T> work) {
                return work.get();
            }

            @Override
            public <T> T write(Supplier<T> work) {
                return work.get();
            }

            @Override
            public void write(Runnable work) {
                work.run();
            }
        };
    }
}
