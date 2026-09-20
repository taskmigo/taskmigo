package io.taskmigo.authorization.role.domain.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoleHierarchyTest {

    private static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LEFT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RIGHT = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID LEAF = UUID.fromString("00000000-0000-0000-0000-000000000004");

    /**
     * Verifies Role hierarchy direction resolves descendants from parent to child.
     *
     * Given: a diamond-shaped parent-to-child graph.
     * Expect: every transitive descendant is returned once in deterministic id order.
     */
    @Test
    @DisplayName("resolves transitive descendants once in deterministic order")
    void shouldResolveTransitiveDescendantsOnceWhenHierarchyHasSharedPaths() {
        // Arrange
        RoleHierarchy hierarchy = RoleHierarchy.from(
            Map.of(ROOT, List.of(RIGHT, LEFT), LEFT, List.of(LEAF), RIGHT, List.of(LEAF))
        );

        // Act
        List<UUID> descendants = hierarchy.descendants(ROOT);

        // Assert
        assertThat(descendants).containsExactly(LEFT, RIGHT, LEAF);
    }

    /**
     * Verifies traversal remains bounded even if persisted graph data is already cyclic.
     *
     * Given: a restored graph containing a cycle.
     * Expect: descendant traversal terminates and does not repeat the root.
     */
    @Test
    @DisplayName("terminates traversal for cyclic persisted data")
    void shouldTerminateTraversalWhenPersistedDataIsCyclic() {
        // Arrange
        RoleHierarchy hierarchy = RoleHierarchy.from(Map.of(ROOT, List.of(LEFT), LEFT, List.of(ROOT)));

        // Act
        List<UUID> descendants = hierarchy.descendants(ROOT);

        // Assert
        assertThat(descendants).containsExactly(LEFT);
    }

    /**
     * Verifies a hierarchy mutation rejects indirect cycles.
     *
     * Given: ROOT reaches LEFT which reaches LEAF.
     * Expect: making LEAF a parent of ROOT is rejected.
     */
    @Test
    @DisplayName("rejects a replacement that creates an indirect cycle")
    void shouldRejectReplacementWhenItCreatesAnIndirectCycle() {
        // Arrange
        RoleHierarchy hierarchy = RoleHierarchy.from(Map.of(ROOT, List.of(LEFT), LEFT, List.of(LEAF)));

        // Act + Assert
        assertThatThrownBy(() -> hierarchy.replacingChildren(LEAF, List.of(ROOT)))
            .isInstanceOf(RoleHierarchyException.class)
            .hasMessage("Role hierarchy must be acyclic");
    }

    /**
     * Verifies self inheritance is rejected.
     *
     * Given: a Role with no children.
     * Expect: replacing its children with itself is rejected as a cycle.
     */
    @Test
    @DisplayName("rejects a self cycle")
    void shouldRejectReplacementWhenItCreatesASelfCycle() {
        // Arrange
        RoleHierarchy hierarchy = RoleHierarchy.from(Map.of(ROOT, List.of()));

        // Act + Assert
        assertThatThrownBy(() -> hierarchy.replacingChildren(ROOT, List.of(ROOT)))
            .isInstanceOf(RoleHierarchyException.class)
            .hasMessage("Role hierarchy must be acyclic");
    }

    /**
     * Verifies direct parent-child reversal is rejected.
     *
     * Given: ROOT directly owns LEFT as a child.
     * Expect: making LEFT a parent of ROOT is rejected.
     */
    @Test
    @DisplayName("rejects a direct cycle")
    void shouldRejectReplacementWhenItCreatesADirectCycle() {
        // Arrange
        RoleHierarchy hierarchy = RoleHierarchy.from(Map.of(ROOT, List.of(LEFT)));

        // Act + Assert
        assertThatThrownBy(() -> hierarchy.replacingChildren(LEFT, List.of(ROOT)))
            .isInstanceOf(RoleHierarchyException.class)
            .hasMessage("Role hierarchy must be acyclic");
    }

    /**
     * Verifies direct child replacement uses set semantics without changing unrelated edges.
     *
     * Given: ROOT and LEFT have independent child edges.
     * Expect: duplicate requested ROOT children collapse while LEFT's edge remains unchanged.
     */
    @Test
    @DisplayName("replaces children without changing other edges")
    void shouldReplaceChildrenWhenNewChildrenAreProvided() {
        // Arrange
        RoleHierarchy hierarchy = RoleHierarchy.from(Map.of(ROOT, List.of(LEFT), LEFT, List.of(LEAF)));

        // Act
        RoleHierarchy replaced = hierarchy.replacingChildren(ROOT, List.of(RIGHT, RIGHT));

        // Assert
        assertThat(replaced.descendants(ROOT)).containsExactly(RIGHT);
        assertThat(replaced.descendants(LEFT)).containsExactly(LEAF);
    }

    /**
     * Verifies effective Role traversal deduplicates shared descendants across multiple roots.
     *
     * Given: two roots that can reach the same leaf.
     * Expect: reachable roles contain each Role once in deterministic id order.
     */
    @Test
    @DisplayName("resolves multiple roots and shared descendants once")
    void shouldResolveSharedDescendantsOnceWhenMultipleRootsAreProvided() {
        // Arrange
        RoleHierarchy hierarchy = RoleHierarchy.from(
            Map.of(ROOT, List.of(LEFT), LEFT, List.of(LEAF), RIGHT, List.of(LEAF))
        );

        // Act
        List<UUID> reachable = hierarchy.reachableFrom(List.of(RIGHT, ROOT));

        // Assert
        assertThat(reachable).containsExactly(ROOT, LEFT, RIGHT, LEAF);
    }

    /**
     * Verifies unknown Role ids do not become synthetic graph nodes during reads.
     *
     * Given: a graph containing only ROOT.
     * Expect: querying descendants for another id returns an empty result.
     */
    @Test
    @DisplayName("returns no descendants for an unknown root")
    void shouldReturnNoDescendantsWhenRootIsUnknown() {
        // Arrange
        RoleHierarchy hierarchy = RoleHierarchy.from(Map.of(ROOT, List.of()));

        // Act
        List<UUID> descendants = hierarchy.descendants(LEFT);

        // Assert
        assertThat(descendants).isEmpty();
    }
}
