package io.taskmigo.authorization.provisioning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.role.internal.RoleStore;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementDefinition;
import io.taskmigo.authorization.statement.StatementPolicyValidator;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.authorization.statement.internal.StatementStore;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultAuthorizationProvisioningServiceTest {

    @Test
    @DisplayName("reports a typed provisioning failure when a managed Role reference is missing")
    void shouldReportProvisioningFailureWhenManagedRoleIsMissing() {
        RoleStore roles = mock(RoleStore.class);
        when(roles.findByName("Missing Role")).thenReturn(Optional.empty());
        var service = service(roles, mock(StatementStore.class), mock(StatementPolicyValidator.class));

        assertThatThrownBy(() -> service.requireRole("Missing Role"))
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
        when(statements.findIdByName("projects.read")).thenReturn(Optional.of(id));
        var service = service(roles, statements, validator);

        UUID result = service.reconcileStatement(
            "projects.read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/projects",
            "true"
        );

        assertThat(result).isEqualTo(id);
        verify(statements).update(id, definition);
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
