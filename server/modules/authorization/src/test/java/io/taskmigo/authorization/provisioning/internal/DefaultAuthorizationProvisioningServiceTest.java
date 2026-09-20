package io.taskmigo.authorization.provisioning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.role.internal.RoleStore;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.authorization.statement.application.StatementCommandService;
import io.taskmigo.authorization.statement.application.StatementMutationResult;
import io.taskmigo.authorization.statement.domain.Statement;
import io.taskmigo.foundation.ReconciliationAction;
import io.taskmigo.foundation.ReconciliationResult;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class DefaultAuthorizationProvisioningServiceTest {

    /**
     * Verifies managed Statement creation delegates to the canonical Statement command path.
     *
     * Given: command reconciliation reports a newly created Statement.
     * Expect: provisioning maps the mutation to the existing ADDED reconciliation contract.
     */
    @Test
    @DisplayName("reports added when canonical statement command creates managed state")
    void shouldReportAddedWhenManagedStatementIsCreated() {
        // Arrange
        StatementCommandService commands = mock(StatementCommandService.class);
        UUID id = UUID.randomUUID();
        when(
            commands.reconcileManaged(
                "projects_read",
                null,
                Effect.ALLOW,
                Scope.REQUEST,
                "GET",
                "/projects",
                "return true;"
            )
        ).thenReturn(new StatementMutationResult(id, true, true));
        var service = service(mock(RoleStore.class), commands);

        // Act
        ReconciliationResult<UUID> result = service.reconcileStatement(
            "projects_read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "return true;"
        );

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.ADDED));
    }

    /**
     * Verifies managed Statement updates use the same aggregate mutation result as runtime code.
     *
     * Given: command reconciliation reports changed state on an existing Statement.
     * Expect: provisioning preserves the id and reports UPDATED.
     */
    @Test
    @DisplayName("reports updated when canonical statement command changes managed state")
    void shouldReportUpdatedWhenManagedStatementChanges() {
        // Arrange
        StatementCommandService commands = mock(StatementCommandService.class);
        UUID id = UUID.randomUUID();
        when(
            commands.reconcileManaged(
                "projects_read",
                "changed",
                Effect.DENY,
                Scope.REQUEST,
                "GET",
                "/projects",
                "return false;"
            )
        ).thenReturn(new StatementMutationResult(id, false, true));
        var service = service(mock(RoleStore.class), commands);

        // Act
        ReconciliationResult<UUID> result = service.reconcileStatement(
            "projects_read",
            "changed",
            Effect.DENY,
            Scope.REQUEST,
            "GET",
            "/projects",
            "return false;"
        );

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UPDATED));
    }

    /**
     * Verifies managed Statement reconciliation remains idempotent when canonical state is unchanged.
     *
     * Given: command reconciliation reports an existing unchanged Statement.
     * Expect: provisioning returns UNCHANGED without a persistence-shaped Statement store dependency.
     */
    @Test
    @DisplayName("reports unchanged when canonical statement command finds identical managed state")
    void shouldReportUnchangedWhenManagedStatementIsIdentical() {
        // Arrange
        StatementCommandService commands = mock(StatementCommandService.class);
        UUID id = UUID.randomUUID();
        when(
            commands.reconcileManaged(
                "projects_read",
                null,
                Effect.ALLOW,
                Scope.REQUEST,
                "GET",
                "/projects",
                "return true;"
            )
        ).thenReturn(new StatementMutationResult(id, false, false));
        var service = service(mock(RoleStore.class), commands);

        // Act
        ReconciliationResult<UUID> result = service.reconcileStatement(
            "projects_read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "return true;"
        );

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UNCHANGED));
    }

    /**
     * Verifies managed Statement lookup is served through the canonical command application contract.
     *
     * Given: a persisted managed Statement restored by code.
     * Expect: requireStatement returns the aggregate id.
     */
    @Test
    @DisplayName("requires managed statement through canonical command service")
    void shouldReturnStatementIdWhenManagedStatementExists() {
        // Arrange
        StatementCommandService commands = mock(StatementCommandService.class);
        Statement statement = statement("projects_read");
        when(commands.findByCode("projects_read")).thenReturn(Optional.of(statement));
        var service = service(mock(RoleStore.class), commands);

        // Act
        UUID id = service.requireStatement("projects_read");

        // Assert
        assertThat(id).isEqualTo(statement.id());
    }

    /**
     * Verifies a missing managed Statement remains a typed provisioning failure.
     *
     * Given: no canonical Statement exists for the managed reference code.
     * Expect: requireStatement raises AuthorizationProvisioningException.
     */
    @Test
    @DisplayName("reports provisioning failure when managed statement reference is missing")
    void shouldReportProvisioningFailureWhenManagedStatementIsMissing() {
        // Arrange
        StatementCommandService commands = mock(StatementCommandService.class);
        when(commands.findByCode("projects_read")).thenReturn(Optional.empty());
        var service = service(mock(RoleStore.class), commands);

        // Act + Assert
        assertThatThrownBy(() -> service.requireStatement("projects_read"))
            .isInstanceOf(AuthorizationProvisioningException.class)
            .hasMessageContaining("Managed authorization Statement does not exist");
    }

    /**
     * Verifies managed Statement deletion goes through the canonical command service.
     *
     * Given: an existing Statement resolved by managed code.
     * Expect: the aggregate is deleted and provisioning reports removal.
     */
    @Test
    @DisplayName("deletes managed statement through canonical command service")
    void shouldDeleteStatementWhenManagedStatementExists() {
        // Arrange
        StatementCommandService commands = mock(StatementCommandService.class);
        Statement statement = statement("projects_read");
        when(commands.findByCode("projects_read")).thenReturn(Optional.of(statement));
        var service = service(mock(RoleStore.class), commands);

        // Act
        boolean removed = service.deleteStatement("projects_read");

        // Assert
        assertThat(removed).isTrue();
        verify(commands).delete(statement);
    }

    /**
     * Verifies existing Role provisioning behavior remains unchanged while Statement persistence is refactored.
     *
     * Given: no persisted Role and a runtime Role service that creates one.
     * Expect: managed reconciliation reports ADDED and retains Statement existence validation through StatementService.
     */
    @Test
    @DisplayName("creates missing managed role through existing role path")
    void shouldCreateRoleWhenManagedRoleIsMissing() {
        // Arrange
        RoleStore roles = mock(RoleStore.class);
        RoleService roleService = mock(RoleService.class);
        StatementService statementService = mock(StatementService.class);
        UUID id = UUID.randomUUID();
        when(roles.findByCode("reader")).thenReturn(Optional.empty());
        when(roleService.createRole("reader", "Reader", null, Set.of())).thenReturn(id);
        var service = new DefaultAuthorizationProvisioningService(
            roleService,
            roles,
            statementService,
            mock(StatementCommandService.class)
        );

        // Act
        ReconciliationResult<UUID> result = service.reconcileRole("reader", "Reader", null, Set.of());

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.ADDED));
        verify(statementService).requireStatements(Set.of());
        verify(roleService).createRole("reader", "Reader", null, Set.of());
    }

    /**
     * Verifies unchanged Role reconciliation still avoids a Role persistence update.
     *
     * Given: a persisted Role whose display name, description, and Statement ids match managed state.
     * Expect: provisioning reports UNCHANGED and skips Role update.
     */
    @Test
    @DisplayName("keeps identical managed role unchanged")
    void shouldSkipRoleUpdateWhenManagedRoleStateIsIdentical() {
        // Arrange
        RoleStore roles = mock(RoleStore.class);
        UUID id = UUID.randomUUID();
        when(roles.findByCode("reader")).thenReturn(
            Optional.of(new RoleStore.RoleState(id, "reader", "Reader", null, Set.of(), Set.of()))
        );
        var service = service(roles, mock(StatementCommandService.class));

        // Act
        ReconciliationResult<UUID> result = service.reconcileRole("reader", "Reader", null, Set.of());

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UNCHANGED));
        verify(roles, never()).updateDisplayNameDescriptionAndStatements(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
        );
    }

    /**
     * Verifies missing managed Role references remain typed provisioning failures.
     *
     * Given: no Role exists for the requested code.
     * Expect: requireRole raises AuthorizationProvisioningException.
     */
    @Test
    @DisplayName("reports provisioning failure when managed role reference is missing")
    void shouldReportProvisioningFailureWhenManagedRoleIsMissing() {
        // Arrange
        RoleStore roles = mock(RoleStore.class);
        when(roles.findByCode("missing-role")).thenReturn(Optional.empty());
        var service = service(roles, mock(StatementCommandService.class));

        // Act + Assert
        assertThatThrownBy(() -> service.requireRole("missing-role"))
            .isInstanceOf(AuthorizationProvisioningException.class)
            .hasMessageContaining("Managed authorization Role does not exist");
    }

    private static DefaultAuthorizationProvisioningService service(
        RoleStore roles,
        StatementCommandService commands
    ) {
        return new DefaultAuthorizationProvisioningService(
            mock(RoleService.class),
            roles,
            mock(StatementService.class),
            commands
        );
    }

    private static Statement statement(String code) {
        return Statement.restore(
            UUID.randomUUID(),
            code,
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "return true;"
        );
    }
}
