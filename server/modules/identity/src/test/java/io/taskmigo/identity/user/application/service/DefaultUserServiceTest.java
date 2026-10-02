package io.taskmigo.identity.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.membership.application.port.in.api.MembershipService;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.out.UserAuditAppender;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import io.taskmigo.identity.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
     * Verifies that a User statement mutation is serialized before state and audit are written.
     *
     * Given: an existing User whose direct statement assignment changes from one ID to another.
     * Expect: the User lock is acquired first, assignment changes, then one synchronous audit record is appended.
     */
    @Test
    @DisplayName("locks and audits when direct statements change")
    void shouldLockAndAppendAuditWhenDirectStatementsChange() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID beforeId = UUID.randomUUID();
        UUID afterId = UUID.randomUUID();
        UserMutationActor actor = new UserMutationActor(UUID.randomUUID(), "operator");
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        UserCommandService commands = Mockito.mock(UserCommandService.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        UserAuditAppender audits = Mockito.mock(UserAuditAppender.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(commands.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(queries.statementIds(subject)).thenReturn(Set.of(beforeId));
        DefaultUserService service = service(users, commands, queries, assignments, audits);

        // Act
        service.setStatements(userId, List.of(afterId), actor);

        // Assert
        var order = inOrder(commands, queries, assignments, audits);
        order.verify(commands).findByIdForUpdate(userId);
        order.verify(queries).statementIds(subject);
        order.verify(assignments).setStatements(subject, Set.of(afterId));
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        order.verify(audits).append(event.capture());
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
     * Verifies that a no-op User statement mutation still acquires the User lock but writes no audit.
     *
     * Given: a User already has exactly the requested direct statement set.
     * Expect: the lock is acquired and neither assignment nor audit persistence is invoked.
     */
    @Test
    @DisplayName("locks but does not audit unchanged direct statements")
    void shouldNotAppendAuditWhenDirectStatementsAreUnchanged() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        UserCommandService commands = Mockito.mock(UserCommandService.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        UserAuditAppender audits = Mockito.mock(UserAuditAppender.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(commands.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(queries.statementIds(subject)).thenReturn(Set.of(statementId));
        DefaultUserService service = service(users, commands, queries, assignments, audits);

        // Act
        service.setStatements(userId, List.of(statementId), new UserMutationActor(UUID.randomUUID(), "operator"));

        // Assert
        verify(commands).findByIdForUpdate(userId);
        verify(assignments, never()).setStatements(any(), any());
        verify(audits, never()).append(any());
    }

    /**
     * Verifies that a User Role mutation is serialized before state and audit are written.
     *
     * Given: an existing User whose direct Role assignment changes from one ID to another.
     * Expect: the User lock is acquired first, assignment changes, then one synchronous audit record is appended.
     */
    @Test
    @DisplayName("locks and audits when direct roles change")
    void shouldLockAndAppendAuditWhenDirectRolesChange() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID beforeId = UUID.randomUUID();
        UUID afterId = UUID.randomUUID();
        UserMutationActor actor = new UserMutationActor(UUID.randomUUID(), "operator");
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        UserCommandService commands = Mockito.mock(UserCommandService.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        UserAuditAppender audits = Mockito.mock(UserAuditAppender.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(commands.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(queries.roleIds(subject)).thenReturn(Set.of(beforeId));
        DefaultUserService service = service(users, commands, queries, assignments, audits);

        // Act
        service.setRoles(userId, List.of(afterId), actor);

        // Assert
        var order = inOrder(commands, queries, assignments, audits);
        order.verify(commands).findByIdForUpdate(userId);
        order.verify(queries).roleIds(subject);
        order.verify(assignments).setRoles(subject, Set.of(afterId));
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        order.verify(audits).append(event.capture());
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
     * Verifies that a no-op User Role mutation still acquires the User lock but writes no audit.
     *
     * Given: a User already has exactly the requested direct Role set.
     * Expect: the lock is acquired and neither assignment nor audit persistence is invoked.
     */
    @Test
    @DisplayName("locks but does not audit unchanged direct roles")
    void shouldNotAppendAuditWhenDirectRolesAreUnchanged() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        UserCommandService commands = Mockito.mock(UserCommandService.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        UserAuditAppender audits = Mockito.mock(UserAuditAppender.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(commands.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(queries.roleIds(subject)).thenReturn(Set.of(roleId));
        DefaultUserService service = service(users, commands, queries, assignments, audits);

        // Act
        service.setRoles(userId, List.of(roleId), new UserMutationActor(UUID.randomUUID(), "operator"));

        // Assert
        verify(commands).findByIdForUpdate(userId);
        verify(assignments, never()).setRoles(any(), any());
        verify(audits, never()).append(any());
    }

    private static DefaultUserService service(
        UserQueryRepository users,
        UserCommandService commands,
        SubjectGrantQueryService queries,
        SubjectGrantAssignmentService assignments,
        UserAuditAppender audits
    ) {
        return new DefaultUserService(
            users,
            commands,
            queries,
            assignments,
            Mockito.mock(MembershipService.class),
            Mockito.mock(ConfigurationService.class),
            audits,
            directTransactions(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static User activeUser(UUID id) {
        return User.restore(id, "test-user", Set.of(), "Test", "User", UserStatus.ACTIVE, null);
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
