package io.taskmigo.authorization.provisioning.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningException;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.role.internal.RoleStore;
import io.taskmigo.authorization.statement.StatementPolicyValidator;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.authorization.statement.internal.StatementStore;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultAuthorizationProvisioningServiceTest {

    @Test
    @DisplayName("reports a typed provisioning failure when a managed Role reference is missing")
    void shouldReportProvisioningFailureWhenManagedRoleIsMissing() {
        RoleStore roles = mock(RoleStore.class);
        when(roles.findByName("Missing Role")).thenReturn(Optional.empty());
        var service = new DefaultAuthorizationProvisioningService(
            mock(RoleService.class),
            roles,
            mock(StatementService.class),
            mock(StatementStore.class),
            mock(StatementPolicyValidator.class)
        );

        assertThatThrownBy(() -> service.requireRole("Missing Role"))
            .isInstanceOf(AuthorizationProvisioningException.class)
            .hasMessageContaining("Managed authorization Role does not exist");
    }
}
