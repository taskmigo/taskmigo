package io.taskmigo.authorization.provisioning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningResult;
import io.taskmigo.authorization.role.application.RoleCommandService;
import io.taskmigo.authorization.role.application.RoleHierarchyRepository;
import io.taskmigo.authorization.role.application.RoleMutationResult;
import io.taskmigo.authorization.role.domain.Role;
import io.taskmigo.authorization.role.domain.hierarchy.RoleHierarchy;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.authorization.statement.application.StatementCommandService;
import io.taskmigo.authorization.statement.application.StatementMutationResult;
import io.taskmigo.authorization.statement.domain.Statement;
import java.util.Map;
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
        var service = service(mock(RoleCommandService.class), commands, mock(StatementService.class));

        // Act
        AuthorizationProvisioningResult<UUID> result = service.reconcileStatement(
            "projects_read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "return true;"
        );

        // Assert
        assertThat(result).isEqualTo(
            new AuthorizationProvisioningResult<>(id, AuthorizationProvisioningResult.Change.CREATED)
        );
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
        var service = service(mock(RoleCommandService.class), commands, mock(StatementService.class));

        // Act
        AuthorizationProvisioningResult<UUID> result = service.reconcileStatement(
            "projects_read",
            "changed",
            Effect.DENY,
            Scope.REQUEST,
            "GET",
            "/projects",
            "return false;"
        );

        // Assert
        assertThat(result).isEqualTo(
            new AuthorizationProvisioningResult<>(id, AuthorizationProvisioningResult.Change.UPDATED)
        );
    }

    /**
     * Verifies managed Statement reconciliation remains idempotent when canonical state is unchanged.
     *
     * Given: command reconciliation reports an existing unchanged Statement.
     * Expect: provisioning returns UNCHANGED.
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
        var service = service(mock(RoleCommandService.class), commands, mock(StatementService.class));

        // Act
        AuthorizationProvisioningResult<UUID> result = service.reconcileStatement(
            "projects_read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "return true;"
        );

        // Assert
        assertThat(result).isEqualTo(
            new AuthorizationProvisioningResult<>(id, AuthorizationProvisioningResult.Change.UNCHANGED)
        );
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
        var service = service(mock(RoleCommandService.class), commands, mock(StatementService.class));

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
        var service = service(mock(RoleCommandService.class), commands, mock(StatementService.class));

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
        var service = service(mock(RoleCommandService.class), commands, mock(StatementService.class));

        // Act
        boolean removed = service.deleteStatement("projects_read");

        // Assert
        assertThat(removed).isTrue();
        verify(commands).delete(statement);
    }

    /**
     * Verifies managed Role creation delegates to the canonical Role command path.
     *
     * Given: valid direct Statement references and command reconciliation that creates a Role.
     * Expect: provisioning validates Statements and maps the Role mutation to ADDED.
     */
    @Test
    @DisplayName("creates missing managed role through canonical role command")
    void shouldCreateRoleWhenManagedRoleIsMissing() {
        // Arrange
        RoleCommandService roles = mock(RoleCommandService.class);
        StatementService statements = mock(StatementService.class);
        UUID id = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        when(roles.reconcileManaged("reader", "Reader", null, Set.of(statementId))).thenReturn(
            new RoleMutationResult(id, true, true)
        );
        RoleHierarchyRepository hierarchies = mock(RoleHierarchyRepository.class);
        RoleHierarchy hierarchy = RoleHierarchy.from(Map.of(id, Set.of()));
        when(hierarchies.loadForMutation()).thenReturn(hierarchy);
        var service = new DefaultAuthorizationProvisioningService(
            roles,
            hierarchies,
            statements,
            mock(StatementCommandService.class)
        );

        // Act
        AuthorizationProvisioningResult<UUID> result = service.reconcileRole(
            "reader",
            "Reader",
            null,
            Set.of(statementId)
        );

        // Assert
        assertThat(result).isEqualTo(
            new AuthorizationProvisioningResult<>(id, AuthorizationProvisioningResult.Change.CREATED)
        );
        verify(statements).requireStatements(Set.of(statementId));
        verify(hierarchies).synchronize(hierarchy);
    }

    /**
     * Verifies unchanged managed Role reconciliation avoids persistence-shaped provisioning behavior.
     *
     * Given: canonical Role reconciliation reports no state change.
     * Expect: provisioning returns UNCHANGED without a second mutation.
     */
    @Test
    @DisplayName("keeps identical managed role unchanged")
    void shouldSkipRoleUpdateWhenManagedRoleStateIsIdentical() {
        // Arrange
        RoleCommandService roles = mock(RoleCommandService.class);
        UUID id = UUID.randomUUID();
        when(roles.reconcileManaged("reader", "Reader", null, Set.of())).thenReturn(
            new RoleMutationResult(id, false, false)
        );
        var service = service(roles, mock(StatementCommandService.class), mock(StatementService.class));

        // Act
        AuthorizationProvisioningResult<UUID> result = service.reconcileRole("reader", "Reader", null, Set.of());

        // Assert
        assertThat(result).isEqualTo(
            new AuthorizationProvisioningResult<>(id, AuthorizationProvisioningResult.Change.UNCHANGED)
        );
        verify(roles, never()).delete(ArgumentMatchers.any());
    }

    /**
     * Verifies missing managed Role references remain typed provisioning failures.
     *
     * Given: no Role aggregate exists for the requested code.
     * Expect: requireRole raises AuthorizationProvisioningException.
     */
    @Test
    @DisplayName("reports provisioning failure when managed role reference is missing")
    void shouldReportProvisioningFailureWhenManagedRoleIsMissing() {
        // Arrange
        RoleCommandService roles = mock(RoleCommandService.class);
        when(roles.findByCode("missing-role")).thenReturn(Optional.empty());
        var service = service(roles, mock(StatementCommandService.class), mock(StatementService.class));

        // Act + Assert
        assertThatThrownBy(() -> service.requireRole("missing-role"))
            .isInstanceOf(AuthorizationProvisioningException.class)
            .hasMessageContaining("Managed authorization Role does not exist");
    }

    /**
     * Verifies managed Role deletion delegates to the canonical Role command path.
     *
     * Given: an existing managed Role resolved by stable code.
     * Expect: provisioning deletes the aggregate through RoleCommandService and reports removal.
     */
    @Test
    @DisplayName("deletes managed role through canonical command service")
    void shouldDeleteRoleWhenManagedRoleExists() {
        // Arrange
        RoleCommandService roles = mock(RoleCommandService.class);
        Role role = Role.restore(UUID.randomUUID(), "reader", "Reader", null, Set.of());
        when(roles.findByCode("reader")).thenReturn(Optional.of(role));
        var service = service(roles, mock(StatementCommandService.class), mock(StatementService.class));

        // Act
        boolean removed = service.deleteRole("reader");

        // Assert
        assertThat(removed).isTrue();
        verify(roles).delete(role);
    }

    private static DefaultAuthorizationProvisioningService service(
        RoleCommandService roles,
        StatementCommandService statements,
        StatementService statementService
    ) {
        return new DefaultAuthorizationProvisioningService(
            roles,
            mock(RoleHierarchyRepository.class),
            statementService,
            statements
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
