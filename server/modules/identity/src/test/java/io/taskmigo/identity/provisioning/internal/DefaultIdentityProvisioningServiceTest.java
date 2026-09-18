package io.taskmigo.identity.provisioning.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.identity.provisioning.IdentityProvisioningException;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.internal.UserStore;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultIdentityProvisioningServiceTest {

    @Test
    @DisplayName("reports a typed provisioning failure when a new system User has no initial password")
    void shouldReportProvisioningFailureWhenSystemUserPasswordIsMissing() {
        UserStore users = mock(UserStore.class);
        when(users.findByUsername(SystemUser.USERNAME)).thenReturn(Optional.empty());
        var service = new DefaultIdentityProvisioningService(users, mock(SubjectGrantService.class));

        assertThatThrownBy(() -> service.reconcileSystemUser(null))
            .isInstanceOf(IdentityProvisioningException.class)
            .hasMessageContaining("Initial password hash is required");
    }
}
