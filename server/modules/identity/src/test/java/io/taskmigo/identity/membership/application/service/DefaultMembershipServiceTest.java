package io.taskmigo.identity.membership.application.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.identity.membership.application.port.out.MembershipRepository;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@NullMarked
class DefaultMembershipServiceTest {

    /**
     * Verifies Membership replacement mutates only changed direct memberships while holding the User mutation lock.
     *
     * Given: a User belongs to group A and desired membership contains group B only.
     * Expect: the User row is locked before membership state is read and only the changed memberships are persisted.
     */
    @Test
    @DisplayName("updates only changed direct memberships under the user lock")
    void shouldMutateOnlyChangedMembershipsWhenDesiredGroupsDiffer() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        UUID requested = UUID.randomUUID();
        MembershipRepository memberships = mock(MembershipRepository.class);
        when(memberships.groupsForUser(userId)).thenReturn(List.of(current));
        GroupService groups = mock(GroupService.class);
        UserCommandService users = mock(UserCommandService.class);
        when(users.lock(userId)).thenReturn(true);
        var service = new DefaultMembershipService(memberships, groups, users, directTransactions());

        // Act
        service.setGroupsForUser(userId, Set.of(requested));

        // Assert
        verify(users).lock(userId);
        verify(groups).requireGroups(Set.of(requested));
        verify(memberships).add(requested, userId);
        verify(memberships).remove(current, userId);
    }

    /**
     * Verifies duplicate requested memberships are collapsed before persistence.
     *
     * Given: the desired collection repeats the same Group id and the User has no current memberships.
     * Expect: the User is locked and the membership repository receives one targeted add.
     */
    @Test
    @DisplayName("deduplicates desired memberships before persistence")
    void shouldAddMembershipOnceWhenDesiredGroupsContainDuplicates() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        MembershipRepository memberships = mock(MembershipRepository.class);
        when(memberships.groupsForUser(userId)).thenReturn(List.of());
        GroupService groups = mock(GroupService.class);
        UserCommandService users = mock(UserCommandService.class);
        when(users.lock(userId)).thenReturn(true);
        var service = new DefaultMembershipService(memberships, groups, users, directTransactions());

        // Act
        service.setGroupsForUser(userId, List.of(groupId, groupId));

        // Assert
        verify(users).lock(userId);
        verify(groups).requireGroups(Set.of(groupId));
        verify(memberships).add(groupId, userId);
        verify(memberships, never()).remove(groupId, userId);
    }

    /**
     * Verifies a targeted membership add participates in the same per-User serialization policy.
     *
     * Given: an existing User and Group.
     * Expect: the User row lock is acquired before the membership row is inserted.
     */
    @Test
    @DisplayName("locks the user before adding a direct membership")
    void shouldLockUserWhenAddingMembership() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        MembershipRepository memberships = mock(MembershipRepository.class);
        GroupService groups = mock(GroupService.class);
        UserCommandService users = mock(UserCommandService.class);
        when(users.lock(userId)).thenReturn(true);
        var service = new DefaultMembershipService(memberships, groups, users, directTransactions());

        // Act
        service.addMember(groupId, userId);

        // Assert
        verify(groups).requireGroups(Set.of(groupId));
        verify(users).lock(userId);
        verify(memberships).add(groupId, userId);
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
