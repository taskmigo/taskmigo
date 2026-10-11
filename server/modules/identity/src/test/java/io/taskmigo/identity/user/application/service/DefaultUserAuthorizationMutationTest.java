package io.taskmigo.identity.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.foundation.DomainFailureType;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.in.internal.UserDeletionLifecycleService;
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

class DefaultUserAuthorizationMutationTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    /**
     * Given: the update-statements Object Authorization query cannot resolve the target.
     * Expect: the mutation returns NOT_FOUND before any unrestricted lock or domain mutation is attempted.
     */
    @Test
    @DisplayName("returns not found before locking when statement target is not authorized")
    void shouldReturnNotFoundBeforeLockingWhenStatementTargetIsNotAuthorized() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserQueryRepository users = mock(UserQueryRepository.class);
        UserCommandService commands = mock(UserCommandService.class);
        SubjectGrantAssignmentService assignments = mock(SubjectGrantAssignmentService.class);
        ObjectAuthorizationPredicate<UserInfo> authorization = authorization();
        when(users.findForStatementUpdate(userId, authorization)).thenReturn(Optional.empty());
        DefaultUserService service = service(
            users,
            commands,
            mock(SubjectGrantQueryService.class),
            assignments,
            mock(UserDeletionLifecycleService.class)
        );

        // Act + Assert
        assertThatThrownBy(() ->
            service.setStatements(
                userId,
                List.of(UUID.randomUUID()),
                authorization,
                new UserMutationActor(UUID.randomUUID(), "operator")
            )
        ).isInstanceOfSatisfying(UserException.class, exception ->
            assertThat(exception.type()).isEqualTo(DomainFailureType.NOT_FOUND)
        );
        verify(commands, never()).findByIdForUpdate(any());
        verify(assignments, never()).setStatements(any(), any());
    }

    /**
     * Given: Object Authorization resolves the target but the locked User is retained and therefore read-only.
     * Expect: authorization runs before locking and the domain conflict remains visible as CONFLICT.
     */
    @Test
    @DisplayName("reports domain conflict only after authorized statement target resolution")
    void shouldReportConflictAfterAuthorizedStatementTargetResolution() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserQueryRepository users = mock(UserQueryRepository.class);
        UserCommandService commands = mock(UserCommandService.class);
        SubjectGrantAssignmentService assignments = mock(SubjectGrantAssignmentService.class);
        ObjectAuthorizationPredicate<UserInfo> authorization = authorization();
        when(users.findForStatementUpdate(userId, authorization)).thenReturn(Optional.of(mock(UserInfo.class)));
        when(commands.findByIdForUpdate(userId)).thenReturn(Optional.of(retainedUser(userId)));
        DefaultUserService service = service(
            users,
            commands,
            mock(SubjectGrantQueryService.class),
            assignments,
            mock(UserDeletionLifecycleService.class)
        );

        // Act + Assert
        assertThatThrownBy(() ->
            service.setStatements(
                userId,
                List.of(UUID.randomUUID()),
                authorization,
                new UserMutationActor(UUID.randomUUID(), "operator")
            )
        ).isInstanceOfSatisfying(UserException.class, exception ->
            assertThat(exception.type()).isEqualTo(DomainFailureType.CONFLICT)
        );
        var order = inOrder(users, commands);
        order.verify(users).findForStatementUpdate(userId, authorization);
        order.verify(commands).findByIdForUpdate(userId);
        verify(assignments, never()).setStatements(any(), any());
    }

    /**
     * Given: Object Authorization resolves an active target but referenced Statements fail semantic validation.
     * Expect: validation happens after authorization and locking, then reports UNPROCESSABLE rather than hiding it.
     */
    @Test
    @DisplayName("reports semantic invalidity only after authorized statement target resolution")
    void shouldReportUnprocessableAfterAuthorizedStatementTargetResolution() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        UserQueryRepository users = mock(UserQueryRepository.class);
        UserCommandService commands = mock(UserCommandService.class);
        SubjectGrantQueryService queries = mock(SubjectGrantQueryService.class);
        SubjectGrantAssignmentService assignments = mock(SubjectGrantAssignmentService.class);
        ObjectAuthorizationPredicate<UserInfo> authorization = authorization();
        when(users.findForStatementUpdate(userId, authorization)).thenReturn(Optional.of(mock(UserInfo.class)));
        when(commands.findByIdForUpdate(userId)).thenReturn(Optional.of(activeUser(userId)));
        when(queries.statementIds(any())).thenReturn(Set.of());
        doThrow(new AuthorizationException("One or more Statements do not exist"))
            .when(assignments)
            .setStatements(any(), any());
        DefaultUserService service = service(
            users,
            commands,
            queries,
            assignments,
            mock(UserDeletionLifecycleService.class)
        );

        // Act + Assert
        assertThatThrownBy(() ->
            service.setStatements(
                userId,
                List.of(statementId),
                authorization,
                new UserMutationActor(UUID.randomUUID(), "operator")
            )
        ).isInstanceOfSatisfying(UserException.class, exception -> {
            assertThat(exception.type()).isEqualTo(DomainFailureType.UNPROCESSABLE);
            assertThat(exception).hasMessage("One or more Statements do not exist");
        });
        var order = inOrder(users, commands, assignments);
        order.verify(users).findForStatementUpdate(userId, authorization);
        order.verify(commands).findByIdForUpdate(userId);
        order.verify(assignments).setStatements(any(), any());
    }

    /**
     * Given: the delete Object Authorization query cannot resolve the target.
     * Expect: deletion returns NOT_FOUND without probing or locking the unrestricted target.
     */
    @Test
    @DisplayName("returns not found before locking when delete target is not authorized")
    void shouldReturnNotFoundBeforeLockingWhenDeleteTargetIsNotAuthorized() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserQueryRepository users = mock(UserQueryRepository.class);
        UserCommandService commands = mock(UserCommandService.class);
        UserDeletionLifecycleService deletion = mock(UserDeletionLifecycleService.class);
        ObjectAuthorizationPredicate<UserInfo> authorization = authorization();
        when(users.findForDelete(userId, authorization)).thenReturn(Optional.empty());
        DefaultUserService service = service(
            users,
            commands,
            mock(SubjectGrantQueryService.class),
            mock(SubjectGrantAssignmentService.class),
            deletion
        );

        // Act + Assert
        assertThatThrownBy(() ->
            service.delete(userId, authorization, new UserMutationActor(UUID.randomUUID(), "operator"))
        ).isInstanceOfSatisfying(UserException.class, exception ->
            assertThat(exception.type()).isEqualTo(DomainFailureType.NOT_FOUND)
        );
        verify(commands, never()).findByIdForUpdate(any());
        verify(deletion, never()).delete(any(), any(), any());
    }

    private static DefaultUserService service(
        UserQueryRepository users,
        UserCommandService commands,
        SubjectGrantQueryService queries,
        SubjectGrantAssignmentService assignments,
        UserDeletionLifecycleService deletion
    ) {
        return new DefaultUserService(
            users,
            commands,
            queries,
            assignments,
            deletion,
            mock(UserAuditAppender.class),
            directTransactions(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static ObjectAuthorizationPredicate<UserInfo> authorization() {
        return new ObjectAuthorizationPredicate<>() {
            @Override
            public boolean isAlwaysTrue() {
                return false;
            }

            @Override
            public boolean isAlwaysFalse() {
                return false;
            }
        };
    }

    private static User activeUser(UUID id) {
        return User.restore(id, "active-user", Set.of(), "Active", "User", UserStatus.ACTIVE, null, null, null);
    }

    private static User retainedUser(UUID id) {
        return User.restore(
            id,
            "retained-user",
            Set.of(),
            "Retained",
            "User",
            UserStatus.RETAINED,
            NOW.minusSeconds(60),
            null,
            null
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
