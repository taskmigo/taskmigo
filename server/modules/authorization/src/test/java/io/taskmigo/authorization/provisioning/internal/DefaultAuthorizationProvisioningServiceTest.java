package io.taskmigo.authorization.provisioning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.role.internal.RoleStore;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementDefinition;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.StatementPolicyValidator;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.authorization.statement.internal.StatementStore;
import io.taskmigo.foundation.ReconciliationAction;
import io.taskmigo.foundation.ReconciliationResult;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultAuthorizationProvisioningServiceTest {

    @Test
    @DisplayName("reports a typed provisioning failure when a managed Role reference is missing")
    void shouldReportProvisioningFailureWhenManagedRoleIsMissing() {
        RoleStore roles = mock(RoleStore.class);
        when(roles.findByCode("missing-role")).thenReturn(Optional.empty());
        var service = service(roles, mock(StatementStore.class), mock(StatementPolicyValidator.class));

        assertThatThrownBy(() -> service.requireRole("missing-role"))
            .isInstanceOf(AuthorizationProvisioningException.class)
            .hasMessageContaining("Managed authorization Role does not exist");
    }

    @Test
    @DisplayName("reconciles an existing managed Statement without changing its identifier")
    void shouldUpdateExistingStatementWhenManagedDefinitionIsReconciled() {
        RoleStore roles = mock(RoleStore.class);
        StatementStore statements = mock(StatementStore.class);
        StatementPolicyValidator validator = mock(StatementPolicyValidator.class);
        StatementDefinition definition = new StatementDefinition(
            "projects.read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "true"
        );
        UUID id = UUID.randomUUID();
        when(
            validator.validate("projects.read", null, Effect.ALLOW, Scope.REQUEST, "GET", "/projects", "true")
        ).thenReturn(definition);
        when(statements.findByCode("projects.read")).thenReturn(
            Optional.of(
                new StatementInfo(
                    id,
                    "projects.read",
                    "Old description",
                    Effect.ALLOW,
                    Scope.REQUEST,
                    new TargetInfo(new ApiInfo("GET", "/projects")),
                    "true"
                )
            )
        );
        var service = service(roles, statements, validator);

        ReconciliationResult<UUID> result = service.reconcileStatement(
            "projects.read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "true"
        );

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.action()).isEqualTo(ReconciliationAction.UPDATED);
        verify(statements).update(id, definition);
    }

    /**
     * Verifies that reconciling an identical managed Statement reports no change and skips persistence.
     * Given: a persisted Statement whose every managed field matches the validated requested definition.
     * Expect: the same identifier is returned with `UNCHANGED` and the Statement store is not updated.
     */
    @Test
    @DisplayName("reports unchanged when a managed Statement data is identical")
    void shouldReportUnchangedWhenManagedStatementDataIsIdentical() {
        // Arrange
        RoleStore roles = mock(RoleStore.class);
        StatementStore statements = mock(StatementStore.class);
        StatementPolicyValidator validator = mock(StatementPolicyValidator.class);
        StatementDefinition definition = new StatementDefinition(
            "projects.read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "true"
        );
        UUID id = UUID.randomUUID();
        when(
            validator.validate("projects.read", null, Effect.ALLOW, Scope.REQUEST, "GET", "/projects", "true")
        ).thenReturn(definition);
        when(statements.findByCode("projects.read")).thenReturn(
            Optional.of(
                new StatementInfo(
                    id,
                    "projects.read",
                    null,
                    Effect.ALLOW,
                    Scope.REQUEST,
                    new TargetInfo(new ApiInfo("GET", "/projects")),
                    "true"
                )
            )
        );
        var service = service(roles, statements, validator);

        // Act
        ReconciliationResult<UUID> result = service.reconcileStatement(
            "projects.read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "true"
        );

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UNCHANGED));
        verify(statements, Mockito.never()).update(Mockito.any(), Mockito.any());
    }

    /**
     * Verifies that reconciling a missing managed Statement creates it and reports the creation action.
     * Given: a validated Statement definition whose code is not present in the store.
     * Expect: the created identifier is returned with the `ADDED` action.
     */
    @Test
    @DisplayName("reports an added action when a managed Statement is created")
    void shouldReportAddedActionWhenManagedStatementIsMissing() {
        RoleStore roles = mock(RoleStore.class);
        StatementStore statements = mock(StatementStore.class);
        StatementPolicyValidator validator = mock(StatementPolicyValidator.class);
        StatementDefinition definition = new StatementDefinition(
            "projects.read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "true"
        );
        UUID id = UUID.randomUUID();
        when(
            validator.validate("projects.read", null, Effect.ALLOW, Scope.REQUEST, "GET", "/projects", "true")
        ).thenReturn(definition);
        when(statements.findByCode("projects.read")).thenReturn(Optional.empty());
        when(statements.create(definition)).thenReturn(id);
        var service = service(roles, statements, validator);

        // Arrange
        // The validator and store stubs above represent a missing managed Statement.

        // Act
        ReconciliationResult<UUID> result = service.reconcileStatement(
            "projects.read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "true"
        );

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.ADDED));
        verify(statements).create(definition);
    }

    /**
     * Verifies that deleting an existing managed Statement reports a removal.
     * Given: a managed Statement identifier found by its code.
     * Expect: the Statement is deleted and the operation returns `true`.
     */
    @Test
    @DisplayName("reports removal when an existing managed Statement is deleted")
    void shouldReportRemovalWhenManagedStatementExists() {
        RoleStore roles = mock(RoleStore.class);
        StatementStore statements = mock(StatementStore.class);
        UUID id = UUID.randomUUID();
        when(statements.findIdByCode("projects_read")).thenReturn(Optional.of(id));
        var service = service(roles, statements, mock(StatementPolicyValidator.class));

        // Arrange
        // The statement store contains the managed Statement targeted for deletion.

        // Act
        boolean removed = service.deleteStatement("projects_read");

        // Assert
        assertThat(removed).isTrue();
        verify(statements).delete(id);
    }

    /**
     * Verifies that reconciling a missing managed Role creates it and reports the creation action.
     * Given: a valid Role code with no matching Role in the store.
     * Expect: a new Role identifier is returned with the `ADDED` action.
     */
    @Test
    @DisplayName("reports an added action when a managed Role is missing")
    void shouldReportAddedActionWhenManagedRoleIsMissing() {
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
            mock(StatementStore.class),
            mock(StatementPolicyValidator.class)
        );

        // Arrange
        // The role store has no existing Role and the role service creates the new identifier.

        // Act
        ReconciliationResult<UUID> result = service.reconcileRole("reader", "Reader", null, Set.of());

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.ADDED));
        verify(roleService).createRole("reader", "Reader", null, Set.of());
    }

    /**
     * Verifies that reconciling an existing managed Role reports an update.
     * Given: a persisted Role with the same code and a new display name.
     * Expect: the existing identifier is retained and the Role state is updated.
     */
    @Test
    @DisplayName("reports an updated action when a managed Role exists")
    void shouldReportUpdatedActionWhenManagedRoleExists() {
        // Arrange
        RoleStore roles = mock(RoleStore.class);
        RoleService roleService = mock(RoleService.class);
        StatementService statementService = mock(StatementService.class);
        UUID id = UUID.randomUUID();
        when(roles.findByCode("reader")).thenReturn(
            Optional.of(new RoleStore.RoleState(id, "reader", "Old", null, Set.of(), Set.of()))
        );
        var service = new DefaultAuthorizationProvisioningService(
            roleService,
            roles,
            statementService,
            mock(StatementStore.class),
            mock(StatementPolicyValidator.class)
        );

        // Act
        ReconciliationResult<UUID> result = service.reconcileRole("reader", "Reader", null, Set.of());

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UPDATED));
        verify(roles).updateDisplayNameDescriptionAndStatements(id, "Reader", null, Set.of());
    }

    /**
     * Verifies that reconciling an identical managed Role reports no change and skips persistence.
     * Given: a persisted Role whose display name, description, and Statement assignments match the request.
     * Expect: the existing identifier is returned with `UNCHANGED` and no Role update is invoked.
     */
    @Test
    @DisplayName("reports unchanged when a managed Role data is identical")
    void shouldReportUnchangedWhenManagedRoleDataIsIdentical() {
        // Arrange
        RoleStore roles = mock(RoleStore.class);
        RoleService roleService = mock(RoleService.class);
        StatementService statementService = mock(StatementService.class);
        UUID id = UUID.randomUUID();
        when(roles.findByCode("reader")).thenReturn(
            Optional.of(new RoleStore.RoleState(id, "reader", "Reader", null, Set.of(), Set.of()))
        );
        var service = new DefaultAuthorizationProvisioningService(
            roleService,
            roles,
            statementService,
            mock(StatementStore.class),
            mock(StatementPolicyValidator.class)
        );

        // Act
        ReconciliationResult<UUID> result = service.reconcileRole("reader", "Reader", null, Set.of());

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UNCHANGED));
        verify(roles, Mockito.never()).updateDisplayNameDescriptionAndStatements(
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any()
        );
    }

    /**
     * Verifies that deleting an existing managed Role reports a removal.
     * Given: a Role found by its managed code.
     * Expect: the Role is deleted and the operation returns `true`.
     */
    @Test
    @DisplayName("reports removal when an existing managed Role is deleted")
    void shouldReportRemovalWhenManagedRoleExists() {
        // Arrange
        RoleStore roles = mock(RoleStore.class);
        UUID id = UUID.randomUUID();
        when(roles.findByCode("reader")).thenReturn(
            Optional.of(new RoleStore.RoleState(id, "reader", "Reader", null, Set.of(), Set.of()))
        );
        var service = service(roles, mock(StatementStore.class), mock(StatementPolicyValidator.class));

        // Act
        boolean removed = service.deleteRole("reader");

        // Assert
        assertThat(removed).isTrue();
        verify(roles).delete(id);
    }

    /**
     * Verifies that deleting a missing managed Role does not report a removal.
     * Given: a Role code that is absent from the role store.
     * Expect: deletion returns `false` and no persistence delete is attempted.
     */
    @Test
    @DisplayName("does not report removal when a managed Role is missing")
    void shouldNotReportRemovalWhenManagedRoleIsMissing() {
        RoleStore roles = mock(RoleStore.class);
        when(roles.findByCode("reader")).thenReturn(Optional.empty());
        var service = service(roles, mock(StatementStore.class), mock(StatementPolicyValidator.class));

        // Arrange
        // The role store has no Role matching the requested code.

        // Act
        boolean removed = service.deleteRole("reader");

        // Assert
        assertThat(removed).isFalse();
        verify(roles, Mockito.never()).delete(Mockito.any());
    }

    private static DefaultAuthorizationProvisioningService service(
        RoleStore roles,
        StatementStore statements,
        StatementPolicyValidator validator
    ) {
        return new DefaultAuthorizationProvisioningService(
            mock(RoleService.class),
            roles,
            mock(StatementService.class),
            statements,
            validator
        );
    }
}
