package io.taskmigo.authorization.role.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.role.domain.Role;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JpaRoleEffectiveStatementRepositoryTest {

    /**
     * Verifies effective authorization reads expand only requested Role closure and deduplicate Statement ids.
     *
     * Given: one directly assigned Role, one reachable child Role, and a shared Statement assignment.
     * Expect: the adapter returns the union of direct and inherited Statement ids with bounded repository reads.
     */
    @Test
    @DisplayName("resolves direct and inherited role statements with bounded reads")
    void shouldResolveDistinctStatementsWhenRoleHierarchyContainsReachableChildren() {
        // Arrange
        UUID directRoleId = UUID.randomUUID();
        UUID childRoleId = UUID.randomUUID();
        UUID directStatementId = UUID.randomUUID();
        UUID sharedStatementId = UUID.randomUUID();
        RoleRepository roles = mock(RoleRepository.class);
        RoleHierarchyClosureRepository closures = mock(RoleHierarchyClosureRepository.class);
        when(closures.findAllByIdAncestorRoleIdIn(Set.of(directRoleId))).thenReturn(
            List.of(
                new RoleHierarchyClosureEntity(directRoleId, directRoleId),
                new RoleHierarchyClosureEntity(directRoleId, childRoleId)
            )
        );
        RoleEntity directRole = RoleEntity.from(
            Role.restore(directRoleId, "direct", "Direct", null, Set.of(directStatementId, sharedStatementId))
        );
        RoleEntity childRole = RoleEntity.from(
            Role.restore(childRoleId, "child1", "Child", null, Set.of(sharedStatementId))
        );
        when(roles.findDistinctByIdIn(anyCollection())).thenReturn(List.of(directRole, childRole));
        JpaRoleEffectiveStatementRepository repository = new JpaRoleEffectiveStatementRepository(roles, closures);

        // Act
        Set<UUID> statementIds = repository.statementIdsForRoles(Set.of(directRoleId));

        // Assert
        assertThat(statementIds).containsExactlyInAnyOrder(directStatementId, sharedStatementId);
    }
}
