package io.taskmigo.identity.group.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.foundation.ReconciliationAction;
import io.taskmigo.foundation.ReconciliationResult;
import io.taskmigo.identity.group.internal.GroupStore.GroupState;
import io.taskmigo.identity.user.UserService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultGroupServiceTest {

    /**
     * Verifies that reconciling a missing managed Group creates it and reports the creation action.
     * Given: a Group code with no matching persisted Group.
     * Expect: the result has the `ADDED` action and the Group store receives a create operation.
     */
    @Test
    @DisplayName("reports an added action when a managed Group is missing")
    void shouldReportAddedActionWhenManagedGroupIsMissing() {
        // Arrange
        GroupStore groups = mock(GroupStore.class);
        when(groups.findByCode("global")).thenReturn(Optional.empty());
        when(groups.loadAllForUpdate()).thenReturn(List.of());
        var service = service(groups);

        // Act
        ReconciliationResult<UUID> result = service.reconcile("global", "Global", null, Set.of());

        // Assert
        assertThat(result.action()).isEqualTo(ReconciliationAction.ADDED);
        verify(groups).create(any(GroupState.class));
    }

    /**
     * Verifies that reconciling an existing managed Group reports an update.
     * Given: a persisted Group with the same code and a new display name.
     * Expect: the existing identifier is retained and the Group update operation is invoked.
     */
    @Test
    @DisplayName("reports an updated action when a managed Group exists")
    void shouldReportUpdatedActionWhenManagedGroupExists() {
        // Arrange
        UUID id = UUID.randomUUID();
        GroupStore groups = mock(GroupStore.class);
        GroupState existing = new GroupState(id, "global", "Old", null, Set.of(), Set.of());
        when(groups.findByCode("global")).thenReturn(Optional.of(existing));
        when(groups.find(id)).thenReturn(Optional.of(existing));
        SubjectGrantService grants = mock(SubjectGrantService.class);
        when(grants.roleIds(any())).thenReturn(Set.of());
        var service = service(groups, grants);

        // Act
        ReconciliationResult<UUID> result = service.reconcile("global", "Global", null, Set.of());

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UPDATED));
        verify(groups).updateDisplayNameAndDescription(id, "Global", null);
    }

    /**
     * Verifies that reconciling an identical managed Group reports no change and skips persistence.
     * Given: a persisted Group with the same display name, description, and Role assignments as requested.
     * Expect: the existing identifier is returned with `UNCHANGED`, with no Group or grant update.
     */
    @Test
    @DisplayName("reports unchanged when a managed Group data is identical")
    void shouldReportUnchangedWhenManagedGroupDataIsIdentical() {
        // Arrange
        UUID id = UUID.randomUUID();
        GroupStore groups = mock(GroupStore.class);
        GroupState existing = new GroupState(id, "global", "Global", null, Set.of(), Set.of());
        when(groups.findByCode("global")).thenReturn(Optional.of(existing));
        SubjectGrantService grants = mock(SubjectGrantService.class);
        when(grants.roleIds(any())).thenReturn(Set.of());
        var service = service(groups, grants);

        // Act
        ReconciliationResult<UUID> result = service.reconcile("global", "Global", null, Set.of());

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UNCHANGED));
        verify(groups, never()).updateDisplayNameAndDescription(any(), any(), any());
        verify(grants, never()).setRoles(any(), any());
    }

    /**
     * Verifies that deleting an existing managed Group reports a removal.
     * Given: a Group found by its managed code.
     * Expect: its grants are cleared, the Group is deleted, and the operation returns `true`.
     */
    @Test
    @DisplayName("reports removal when an existing managed Group is deleted")
    void shouldReportRemovalWhenManagedGroupExists() {
        // Arrange
        UUID id = UUID.randomUUID();
        GroupStore groups = mock(GroupStore.class);
        when(groups.findByCode("global")).thenReturn(
            Optional.of(new GroupState(id, "global", "Global", null, Set.of(), Set.of()))
        );
        SubjectGrantService grants = mock(SubjectGrantService.class);
        var service = service(groups, grants);

        // Act
        boolean removed = service.deleteByCode("global");

        // Assert
        assertThat(removed).isTrue();
        verify(grants).setRoles(any(), eq(Set.of()));
        verify(groups).delete(id);
    }

    /**
     * Verifies that deleting a missing managed Group does not report a removal.
     * Given: a Group code that is absent from persistence.
     * Expect: deletion returns `false` and no Group delete operation is invoked.
     */
    @Test
    @DisplayName("does not report removal when a managed Group is missing")
    void shouldNotReportRemovalWhenManagedGroupIsMissing() {
        // Arrange
        GroupStore groups = mock(GroupStore.class);
        when(groups.findByCode("global")).thenReturn(Optional.empty());
        var service = service(groups);

        // Act
        boolean removed = service.deleteByCode("global");

        // Assert
        assertThat(removed).isFalse();
        verify(groups, never()).delete(any());
    }

    private static DefaultGroupService service(GroupStore groups) {
        return service(groups, mock(SubjectGrantService.class));
    }

    private static DefaultGroupService service(GroupStore groups, SubjectGrantService grants) {
        return new DefaultGroupService(groups, mock(UserService.class), grants);
    }
}
