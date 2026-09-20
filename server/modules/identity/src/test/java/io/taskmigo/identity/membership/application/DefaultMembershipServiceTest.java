package io.taskmigo.identity.membership.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.identity.group.application.GroupQueryRepository;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultMembershipServiceTest {

    /**
     * Verifies Membership replacement mutates only changed direct memberships.
     *
     * Given: a User belongs to group A and desired membership contains group B only.
     * Expect: B is added, A is removed, and no full Group hierarchy state is involved.
     */
    @Test
    @DisplayName("updates only changed direct memberships")
    void shouldMutateOnlyChangedMembershipsWhenDesiredGroupsDiffer() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        UUID requested = UUID.randomUUID();
        MembershipRepository memberships = mock(MembershipRepository.class);
        when(memberships.groupsForUser(userId)).thenReturn(List.of(current));
        GroupQueryRepository groups = mock(GroupQueryRepository.class);
        when(groups.containsAll(Set.of(requested))).thenReturn(true);
        UserService users = mock(UserService.class);
        var service = new DefaultMembershipService(memberships, groups, users);

        // Act
        service.setGroupsForUser(userId, Set.of(requested));

        // Assert
        verify(users).require(userId);
        verify(memberships).add(requested, userId);
        verify(memberships).remove(current, userId);
    }

    /**
     * Verifies duplicate requested memberships are collapsed before persistence.
     *
     * Given: the desired collection repeats the same Group id and the User has no current memberships.
     * Expect: the membership repository receives one targeted add.
     */
    @Test
    @DisplayName("deduplicates desired memberships before persistence")
    void shouldAddMembershipOnceWhenDesiredGroupsContainDuplicates() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        MembershipRepository memberships = mock(MembershipRepository.class);
        when(memberships.groupsForUser(userId)).thenReturn(List.of());
        GroupQueryRepository groups = mock(GroupQueryRepository.class);
        when(groups.containsAll(Set.of(groupId))).thenReturn(true);
        UserService users = mock(UserService.class);
        var service = new DefaultMembershipService(memberships, groups, users);

        // Act
        service.setGroupsForUser(userId, List.of(groupId, groupId));

        // Assert
        verify(memberships).add(groupId, userId);
        verify(memberships, never()).remove(groupId, userId);
    }
}
