package io.taskmigo.identity.provisioning.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.subject.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.application.port.in.internal.GroupCommandService;
import io.taskmigo.identity.group.application.port.in.internal.GroupMutationResult;
import io.taskmigo.identity.group.application.port.out.GroupHierarchyRepository;
import io.taskmigo.identity.group.domain.Group;
import io.taskmigo.identity.group.domain.hierarchy.GroupHierarchy;
import io.taskmigo.identity.provisioning.IdentityProvisioningResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

@NullMarked
class DefaultGroupProvisioningServiceTest {

    /**
     * Verifies managed Group creation composes canonical profile state with Access Control grants.
     *
     * Given: the shared Group command path reports a newly created Group.
     * Expect: hierarchy persistence is synchronized, requested Roles are assigned, and reconciliation reports ADDED.
     */
    @Test
    @DisplayName("reports added when the canonical Group command path creates a Group")
    void shouldReportAddedWhenCanonicalGroupMutationCreatesGroup() {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        GroupCommandService groups = mock(GroupCommandService.class);
        when(groups.reconcileManaged("engineering", "Engineering", null)).thenReturn(
            new GroupMutationResult(id, true, true)
        );
        GroupHierarchy hierarchy = GroupHierarchy.from(Map.of(id, List.of()));
        GroupHierarchyRepository hierarchies = mock(GroupHierarchyRepository.class);
        when(hierarchies.loadForMutation()).thenReturn(hierarchy);
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        var service = new DefaultGroupProvisioningService(groups, hierarchies, grantAssignments, grantQueries, directTransactions());

        // Act
        IdentityProvisioningResult<UUID> result = service.reconcileGroup(
            "engineering",
            "Engineering",
            null,
            Set.of(roleId)
        );

        // Assert
        assertThat(result).isEqualTo(new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.CREATED));
        verify(hierarchies).synchronize(hierarchy);
        verify(grantAssignments).setRoles(IdentitySubjects.group(id), Set.of(roleId));
    }

    /**
     * Verifies identical managed Group state remains idempotent.
     *
     * Given: canonical Group profile and direct Role assignments already match desired state.
     * Expect: reconciliation reports UNCHANGED and performs no grant mutation.
     */
    @Test
    @DisplayName("reports unchanged when managed Group and grants already match")
    void shouldReportUnchangedWhenManagedGroupAlreadyMatches() {
        // Arrange
        UUID id = UUID.randomUUID();
        GroupCommandService groups = mock(GroupCommandService.class);
        when(groups.reconcileManaged("engineering", "Engineering", null)).thenReturn(
            new GroupMutationResult(id, false, false)
        );
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        when(grantQueries.roleIds(IdentitySubjects.group(id))).thenReturn(Set.of());
        var service = new DefaultGroupProvisioningService(
            groups,
            mock(GroupHierarchyRepository.class),
            grantAssignments,
            grantQueries,
            directTransactions()
        );

        // Act
        IdentityProvisioningResult<UUID> result = service.reconcileGroup("engineering", "Engineering", null, Set.of());

        // Assert
        assertThat(result).isEqualTo(new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.UNCHANGED));
        verify(grantAssignments, never()).setRoles(IdentitySubjects.group(id), Set.of());
    }

    /**
     * Verifies managed deletion clears grants before deleting the canonical Group and synchronizes remaining hierarchy.
     *
     * Given: an existing Group that connects one remaining Group to another.
     * Expect: direct Roles are cleared, the aggregate is deleted, and the persisted hierarchy excludes the deleted Group.
     */
    @Test
    @DisplayName("deletes an existing managed Group through the canonical command path")
    void shouldDeleteManagedGroupWhenStableCodeExists() {
        // Arrange
        GroupCommandService groups = mock(GroupCommandService.class);
        Group existing = Group.restore(UUID.randomUUID(), "engineering", "Engineering", null);
        when(groups.findByCode("engineering")).thenReturn(Optional.of(existing));
        UUID root = UUID.randomUUID();
        UUID leaf = UUID.randomUUID();
        GroupHierarchy current = GroupHierarchy.from(
            Map.of(root, List.of(existing.id()), existing.id(), List.of(leaf), leaf, List.of())
        );
        GroupHierarchyRepository hierarchies = mock(GroupHierarchyRepository.class);
        when(hierarchies.loadForMutation()).thenReturn(current);
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        var service = new DefaultGroupProvisioningService(groups, hierarchies, grantAssignments, grantQueries, directTransactions());

        // Act
        boolean removed = service.deleteGroup("engineering");

        // Assert
        assertThat(removed).isTrue();
        verify(grantAssignments).setRoles(IdentitySubjects.group(existing.id()), Set.of());
        verify(groups).delete(existing);
        verify(hierarchies).remove(
            ArgumentMatchers.eq(existing.id()),
            ArgumentMatchers.argThat(
                hierarchy ->
                    hierarchy.groupIds().equals(List.of(root, leaf).stream().sorted().toList()) &&
                    hierarchy.reachableFrom(root).equals(List.of(root))
            )
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
