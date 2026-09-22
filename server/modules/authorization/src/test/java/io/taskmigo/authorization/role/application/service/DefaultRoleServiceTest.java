package io.taskmigo.authorization.role.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.application.port.in.internal.RoleCommandService;
import io.taskmigo.authorization.role.application.port.out.RoleHierarchyRepository;
import io.taskmigo.authorization.role.application.port.out.RoleQueryRepository;
import io.taskmigo.authorization.role.domain.hierarchy.RoleHierarchy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@NullMarked
@ExtendWith(MockitoExtension.class)
class DefaultRoleServiceTest {

    @Mock
    private RoleCommandService commands;

    @Mock
    private RoleQueryRepository roles;

    @Mock
    private RoleHierarchyRepository hierarchies;

    /**
     * Verifies runtime creation separates aggregate mutation from hierarchy persistence.
     *
     * Given: an empty hierarchy and a valid Role creation request.
     * Expect: canonical command creation runs once and hierarchy persistence receives no Statement-shaped state.
     */
    @Test
    @DisplayName("creates role through command and hierarchy ports")
    void shouldCreateRoleThroughCanonicalPortsWhenRequestHasNoChildren() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(this.hierarchies.loadForMutation()).thenReturn(RoleHierarchy.from(Map.of()));
        when(this.commands.createRuntime("  administrator  ", "Administrator", null)).thenReturn(id);
        DefaultRoleService service = new DefaultRoleService(
            this.commands,
            this.roles,
            this.hierarchies,
            directTransactions()
        );

        // Act
        UUID created = service.createRole("  administrator  ", "Administrator", null, Set.of());

        // Assert
        assertThat(created).isEqualTo(id);
        verify(this.commands).createRuntime("  administrator  ", "Administrator", null);
        verify(this.hierarchies).replaceChildren(eq(id), eq(Set.of()), any(RoleHierarchy.class));
    }

    /**
     * Verifies hierarchy mutation validates requested child Role existence before persistence.
     *
     * Given: a hierarchy containing the parent but not the requested child.
     * Expect: the mutation fails with the established child-Role diagnostic.
     */
    @Test
    @DisplayName("rejects missing child role before hierarchy persistence")
    void shouldRejectChildReplacementWhenRequestedRoleDoesNotExist() {
        // Arrange
        UUID parent = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(this.hierarchies.loadForMutation()).thenReturn(RoleHierarchy.from(Map.of(parent, Set.of())));
        DefaultRoleService service = new DefaultRoleService(
            this.commands,
            this.roles,
            this.hierarchies,
            directTransactions()
        );

        // Act + Assert
        assertThatThrownBy(() -> service.setChildRoles(parent, Set.of(missing)))
            .isInstanceOf(RoleException.class)
            .hasMessage("One or more child Roles do not exist");
    }

    /**
     * Verifies effective Role reads stay on the closure/projection path rather than aggregate traversal.
     *
     * Given: a requested Role whose closure resolves one additional descendant.
     * Expect: the service queries only projected Roles for closure ids and returns deterministic id order.
     */
    @Test
    @DisplayName("resolves effective roles through bounded read ports")
    void shouldResolveEffectiveRolesWhenClosureReturnsReachableIds() {
        // Arrange
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID child = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(this.roles.containsAll(Set.of(first))).thenReturn(true);
        when(this.hierarchies.descendantRoleIds(Set.of(first))).thenReturn(List.of(first, child));
        when(this.roles.findByIds(List.of(first, child))).thenReturn(
            List.of(
                new RoleInfo(child, "child1", "Child", null, List.of()),
                new RoleInfo(first, "parent", "Parent", null, List.of())
            )
        );
        DefaultRoleService service = new DefaultRoleService(
            this.commands,
            this.roles,
            this.hierarchies,
            directTransactions()
        );

        // Act
        List<RoleInfo> effective = service.effectiveRoles(Set.of(first));

        // Assert
        assertThat(effective).extracting(RoleInfo::id).containsExactly(first, child);
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
