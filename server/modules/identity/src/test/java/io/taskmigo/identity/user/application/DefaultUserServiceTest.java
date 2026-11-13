package io.taskmigo.identity.user.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.audit.application.record.AuditRecorder;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.audit.model.AuditOperation;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.api.SubjectGrantQueryService;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.internal.UserCommandService;
import io.taskmigo.identity.user.application.internal.UserDeletionLifecycleService;
import io.taskmigo.identity.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    /**
     * Verifies that an updated direct Statement assignment is audited after the business mutation.
     *
     * Given: the User is mutable, with one existing Statement and a different requested Statement.
     * Expect: one UPDATE audit with the exact ordered before/after IDs after the assignment.
     */
    @Test
    @DisplayName("audits changed direct Statements after acquiring the User lock")
    void shouldAuditChangedStatements() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID beforeId = UUID.randomUUID();
        UUID afterId = UUID.randomUUID();
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        UserCommandService commands = Mockito.mock(UserCommandService.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        AuditRecorder audits = Mockito.mock(AuditRecorder.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(commands.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(queries.statementIds(subject)).thenReturn(Set.of(beforeId));
        DefaultUserService service = service(users, commands, queries, assignments, audits);

        // Act
        service.setStatements(userId, List.of(afterId));

        // Assert
        var order = inOrder(commands, queries, assignments, audits);
        order.verify(commands).findByIdForUpdate(userId);
        order.verify(queries).statementIds(subject);
        order.verify(assignments).setStatements(subject, Set.of(afterId));
        AuditChange expected = AuditChange.visible(
            "statementIds",
            List.of(beforeId.toString()),
            List.of(afterId.toString())
        );
        order
            .verify(audits)
            .record(
                eq("user"),
                eq(userId),
                eq(AuditOperation.UPDATE),
                argThat(supplier -> supplier.get().equals(List.of(expected)))
            );
    }

    /**
     * Verifies that an unchanged Statement assignment does not create artificial audit history.
     *
     * Given: the requested Statement set equals the persisted set.
     * Expect: the User is locked but neither the assignment nor audit recorder is invoked.
     */
    @Test
    @DisplayName("skips audit when direct Statements are unchanged")
    void shouldNotAuditUnchangedStatements() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        UserCommandService commands = Mockito.mock(UserCommandService.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        AuditRecorder audits = Mockito.mock(AuditRecorder.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(commands.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(queries.statementIds(subject)).thenReturn(Set.of(statementId));

        // Act
        service(users, commands, queries, assignments, audits).setStatements(userId, List.of(statementId));

        // Assert
        verify(commands).findByIdForUpdate(userId);
        verify(assignments, never()).setStatements(any(), any());
        verify(audits, never()).record(any(), any(UUID.class), any(), any());
    }

    /**
     * Verifies that direct Role mutation uses the same audit contract as all managed operations.
     *
     * Given: a mutable User whose direct Role assignment changes.
     * Expect: an UPDATE audit with the exact before/after Role IDs.
     */
    @Test
    @DisplayName("audits changed direct Roles after acquiring the User lock")
    void shouldAuditChangedRoles() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID beforeId = UUID.randomUUID();
        UUID afterId = UUID.randomUUID();
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        UserCommandService commands = Mockito.mock(UserCommandService.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        AuditRecorder audits = Mockito.mock(AuditRecorder.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(commands.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(queries.roleIds(subject)).thenReturn(Set.of(beforeId));
        DefaultUserService service = service(users, commands, queries, assignments, audits);

        // Act
        service.setRoles(userId, List.of(afterId));

        // Assert
        var order = inOrder(commands, queries, assignments, audits);
        order.verify(commands).findByIdForUpdate(userId);
        order.verify(queries).roleIds(subject);
        order.verify(assignments).setRoles(subject, Set.of(afterId));
        AuditChange expected = AuditChange.visible(
            "roleIds",
            List.of(beforeId.toString()),
            List.of(afterId.toString())
        );
        order
            .verify(audits)
            .record(
                eq("user"),
                eq(userId),
                eq(AuditOperation.UPDATE),
                argThat(supplier -> supplier.get().equals(List.of(expected)))
            );
    }

    /**
     * Verifies that an unchanged Role assignment creates no audit record.
     *
     * Given: persisted direct Role IDs already equal the requested IDs.
     * Expect: mutation is skipped and recorder is not invoked.
     */
    @Test
    @DisplayName("skips audit when direct Roles are unchanged")
    void shouldNotAuditUnchangedRoles() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UserQueryRepository users = Mockito.mock(UserQueryRepository.class);
        UserCommandService commands = Mockito.mock(UserCommandService.class);
        SubjectGrantQueryService queries = Mockito.mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = Mockito.mock(SubjectGrantAssignmentService.class);
        AuditRecorder audits = Mockito.mock(AuditRecorder.class);
        SubjectRef subject = IdentitySubjects.user(userId);
        when(commands.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(queries.roleIds(subject)).thenReturn(Set.of(roleId));

        // Act
        service(users, commands, queries, assignments, audits).setRoles(userId, List.of(roleId));

        // Assert
        verify(commands).findByIdForUpdate(userId);
        verify(assignments, never()).setRoles(any(), any());
        verify(audits, never()).record(any(), any(UUID.class), any(), any());
    }

    private static DefaultUserService service(
        UserQueryRepository users,
        UserCommandService commands,
        SubjectGrantQueryService queries,
        SubjectGrantAssignmentService assignments,
        AuditRecorder audits
    ) {
        return new DefaultUserService(
            users,
            commands,
            queries,
            assignments,
            Mockito.mock(UserDeletionLifecycleService.class),
            audits,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static User activeUser(UUID id) {
        return User.restore(id, "test-user", Set.of(), "Test", "User", UserStatus.ACTIVE, null);
    }
}
