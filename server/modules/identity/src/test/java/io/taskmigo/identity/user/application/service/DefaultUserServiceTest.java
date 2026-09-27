package io.taskmigo.identity.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.audit.application.port.in.api.AuditEventService;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditMutationEvent;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultUserServiceTest {

    /**
     * Verifies a runtime Statement replacement snapshots the authenticated actor and actual diff.
     *
     * Given: a User with one direct Statement and an authenticated actor replacing it.
     * Expect: exactly one audit event is published inside the write operation with before/after identifiers.
     */
    @Test
    @DisplayName("publishes audit event for authenticated statement changes")
    void shouldPublishAuditEventWhenAuthenticatedStatementsChange() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID before = UUID.randomUUID();
        UUID after = UUID.randomUUID();
        AuditActor actor = new AuditActor(UUID.randomUUID(), "operator");
        UserQueryRepository users = mock(UserQueryRepository.class);
        SubjectGrantQueryService queries = mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = mock(SubjectGrantAssignmentService.class);
        AuditEventService audit = mock(AuditEventService.class);
        TransactionRunner transactions = directTransactions();
        SubjectRef subject = IdentitySubjects.user(userId);
        when(users.exists(userId)).thenReturn(true);
        when(queries.statementIds(subject)).thenReturn(Set.of(before));
        var service = new DefaultUserService(users, queries, assignments, audit, transactions);

        // Act
        service.setStatements(userId, Set.of(after), actor);

        // Assert
        ArgumentCaptor<AuditMutationEvent> event = ArgumentCaptor.forClass(AuditMutationEvent.class);
        verify(audit).publish(event.capture());
        assertThat(event.getValue().entityType()).isEqualTo("user");
        assertThat(event.getValue().entityId()).isEqualTo(userId);
        assertThat(event.getValue().actor()).isEqualTo(actor);
        assertThat(event.getValue().changes())
            .singleElement()
            .satisfies(change -> {
                assertThat(change.field()).isEqualTo("statementIds");
                assertThat(change.before()).isEqualTo(Set.of(before).stream().map(UUID::toString).toList());
                assertThat(change.after()).isEqualTo(Set.of(after).stream().map(UUID::toString).toList());
            });
    }

    /**
     * Verifies replacing a direct Statement set with the same canonical set does not create misleading audit work.
     *
     * Given: the requested direct Statements already match persistence.
     * Expect: the mutation remains valid but no audit event is published.
     */
    @Test
    @DisplayName("does not publish audit event for no-op statement changes")
    void shouldNotPublishAuditEventWhenStatementsAreUnchanged() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        AuditActor actor = new AuditActor(UUID.randomUUID(), "operator");
        UserQueryRepository users = mock(UserQueryRepository.class);
        SubjectGrantQueryService queries = mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = mock(SubjectGrantAssignmentService.class);
        AuditEventService audit = mock(AuditEventService.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(users.exists(userId)).thenReturn(true);
        when(queries.statementIds(subject)).thenReturn(Set.of(statementId));
        var service = new DefaultUserService(users, queries, assignments, audit, directTransactions());

        // Act
        service.setStatements(userId, Set.of(statementId), actor);

        // Assert
        verify(assignments).setStatements(subject, Set.of(statementId));
        verify(audit, never()).publish(any());
    }

    private static TransactionRunner directTransactions() {
        TransactionRunner transactions = mock(TransactionRunner.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        })
            .when(transactions)
            .write(any(Runnable.class));
        return transactions;
    }
}
