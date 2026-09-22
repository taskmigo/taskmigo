package io.taskmigo.authorization.role.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.application.port.in.internal.RoleMutationResult;
import io.taskmigo.authorization.role.application.port.out.RoleCommandRepository;
import io.taskmigo.authorization.role.application.port.out.RoleHierarchyRepository;
import io.taskmigo.authorization.role.domain.Role;
import io.taskmigo.authorization.role.domain.RoleCode;
import io.taskmigo.authorization.role.domain.hierarchy.RoleHierarchy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultRoleCommandServiceTest {

    @Mock
    private RoleCommandRepository roles;

    @Mock
    private RoleHierarchyRepository hierarchies;

    /**
     * Verifies runtime Role creation persists canonical aggregate state without hierarchy topology.
     *
     * Given: a valid Role profile.
     * Expect: the command repository receives a Role with stable code and no direct Statements.
     */
    @Test
    @DisplayName("creates runtime role through canonical aggregate repository")
    void shouldPersistCanonicalRoleWhenRuntimeCreationIsRequested() {
        // Arrange
        DefaultRoleCommandService service = new DefaultRoleCommandService(this.roles, this.hierarchies);
        ArgumentCaptor<Role> role = ArgumentCaptor.forClass(Role.class);

        // Act
        UUID id = service.createRuntime("reader", " Reader ", null);

        // Assert
        verify(this.roles).save(role.capture());
        assertThat(role.getValue().id()).isEqualTo(id);
        assertThat(role.getValue().code().value()).isEqualTo("reader");
        assertThat(role.getValue().profile().displayName()).isEqualTo("Reader");
        assertThat(role.getValue().statementIds()).isEmpty();
    }

    /**
     * Verifies unchanged managed Role state does not cause a persistence write.
     *
     * Given: an existing Role whose profile and direct Statements equal requested managed state.
     * Expect: reconciliation reports unchanged and skips save.
     */
    @Test
    @DisplayName("keeps identical managed role unchanged")
    void shouldSkipPersistenceWhenManagedRoleStateIsIdentical() {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        Role existing = Role.restore(id, "reader", "Reader", null, Set.of(statementId));
        when(this.roles.findByCode(RoleCode.of("reader"))).thenReturn(Optional.of(existing));
        DefaultRoleCommandService service = new DefaultRoleCommandService(this.roles, this.hierarchies);

        // Act
        RoleMutationResult result = service.reconcileManaged("reader", "Reader", null, Set.of(statementId));

        // Assert
        assertThat(result).isEqualTo(new RoleMutationResult(id, false, false));
        verify(this.roles, never()).save(existing);
    }

    /**
     * Verifies Role-to-Statement replacement uses the aggregate command path.
     *
     * Given: an existing Role and a new direct Statement set.
     * Expect: the aggregate is updated and persisted once.
     */
    @Test
    @DisplayName("replaces direct statements through role aggregate")
    void shouldSaveRoleWhenDirectStatementsChange() {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        Role existing = Role.restore(id, "reader", "Reader", null, Set.of());
        when(this.roles.find(id)).thenReturn(Optional.of(existing));
        DefaultRoleCommandService service = new DefaultRoleCommandService(this.roles, this.hierarchies);

        // Act
        service.replaceStatements(id, Set.of(statementId));

        // Assert
        assertThat(existing.statementIds()).containsExactly(statementId);
        verify(this.roles).save(existing);
    }

    /**
     * Verifies Role-to-Statement replacement preserves the established missing-Role failure contract.
     *
     * Given: no aggregate exists for the requested Role id.
     * Expect: the command fails as a bad Role request.
     */
    @Test
    @DisplayName("rejects statement replacement for missing role")
    void shouldRejectStatementReplacementWhenRoleDoesNotExist() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(this.roles.find(id)).thenReturn(Optional.empty());
        DefaultRoleCommandService service = new DefaultRoleCommandService(this.roles, this.hierarchies);

        // Act + Assert
        assertThatThrownBy(() -> service.replaceStatements(id, Set.of()))
            .isInstanceOf(RoleException.class)
            .hasMessage("Role does not exist");
    }

    /**
     * Verifies managed deletion repairs derived hierarchy closure after canonical Role removal.
     *
     * Given: a hierarchy snapshot containing the Role being deleted.
     * Expect: the aggregate is deleted and closure synchronization receives the graph without that Role.
     */
    @Test
    @DisplayName("synchronizes hierarchy after managed role deletion")
    void shouldSynchronizeRemainingHierarchyWhenManagedRoleIsDeleted() {
        // Arrange
        UUID id = UUID.randomUUID();
        Role role = Role.restore(id, "reader", "Reader", null, Set.of());
        RoleHierarchy hierarchy = RoleHierarchy.from(Map.of(id, Set.of()));
        when(this.hierarchies.loadForMutation()).thenReturn(hierarchy);
        DefaultRoleCommandService service = new DefaultRoleCommandService(this.roles, this.hierarchies);
        ArgumentCaptor<RoleHierarchy> remaining = ArgumentCaptor.forClass(RoleHierarchy.class);

        // Act
        service.delete(role);

        // Assert
        verify(this.hierarchies).remove(eq(id), remaining.capture());
        verify(this.roles).delete(role);
        assertThat(remaining.getValue().contains(id)).isFalse();
    }
}
