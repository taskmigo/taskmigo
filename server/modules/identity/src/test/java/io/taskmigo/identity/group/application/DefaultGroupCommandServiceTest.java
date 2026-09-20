package io.taskmigo.identity.group.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.identity.group.domain.Group;
import io.taskmigo.identity.group.domain.GroupCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultGroupCommandServiceTest {

    /**
     * Verifies managed creation uses the canonical Group aggregate.
     *
     * Given: no Group exists for a padded stable code.
     * Expect: one normalized aggregate is saved and reconciliation reports ADDED state.
     */
    @Test
    @DisplayName("creates a missing managed Group through the canonical aggregate")
    void shouldCreateManagedGroupWhenCodeIsMissing() {
        // Arrange
        GroupCommandRepository groups = mock(GroupCommandRepository.class);
        when(groups.findByCode(GroupCode.of("engineering"))).thenReturn(Optional.empty());
        var service = new DefaultGroupCommandService(groups);
        ArgumentCaptor<Group> saved = ArgumentCaptor.forClass(Group.class);

        // Act
        GroupMutationResult result = service.reconcileManaged(" engineering ", " Engineering ", "managed");

        // Assert
        assertThat(result.created()).isTrue();
        assertThat(result.changed()).isTrue();
        verify(groups).save(saved.capture());
        assertThat(saved.getValue().code().value()).isEqualTo("engineering");
        assertThat(saved.getValue().profile().displayName()).isEqualTo("Engineering");
    }

    /**
     * Verifies an identical managed profile is idempotent.
     *
     * Given: persisted Group state already matches desired managed state.
     * Expect: reconciliation reports no change and performs no save.
     */
    @Test
    @DisplayName("does not save an unchanged managed Group")
    void shouldNotSaveManagedGroupWhenProfileAlreadyMatches() {
        // Arrange
        GroupCommandRepository groups = mock(GroupCommandRepository.class);
        Group existing = Group.restore(UUID.randomUUID(), "engineering", "Engineering", null);
        when(groups.findByCode(GroupCode.of("engineering"))).thenReturn(Optional.of(existing));
        var service = new DefaultGroupCommandService(groups);

        // Act
        GroupMutationResult result = service.reconcileManaged("engineering", "Engineering", null);

        // Assert
        assertThat(result).isEqualTo(new GroupMutationResult(existing.id(), false, false));
        verify(groups, never()).save(existing);
    }
}
