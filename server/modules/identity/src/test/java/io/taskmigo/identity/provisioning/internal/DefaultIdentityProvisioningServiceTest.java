package io.taskmigo.identity.provisioning.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.identity.group.GroupService;
import io.taskmigo.identity.provisioning.IdentityProvisioningException;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.internal.UserStore;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultIdentityProvisioningServiceTest {

    /**
     * Verifies: a new system user cannot be provisioned without a pre-encoded password.
     * Given: no existing system user and a null password hash.
     * Expect: the provisioning boundary raises a typed identity failure.
     */
    @Test
    @DisplayName("reports a typed provisioning failure when a new system User has no initial password")
    void shouldReportProvisioningFailureWhenSystemUserPasswordIsMissing() {
        UserStore users = mock(UserStore.class);
        when(users.findByUsername(SystemUser.USERNAME)).thenReturn(Optional.empty());
        var service = new DefaultIdentityProvisioningService(
            users,
            mock(SubjectGrantService.class),
            mock(GroupService.class)
        );

        assertThatThrownBy(() -> service.reconcileUser("system", null, null, "System", "User", Set.of(), Set.of()))
            .isInstanceOf(IdentityProvisioningException.class)
            .hasMessageContaining("password hash is required");
    }
}
