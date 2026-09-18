package io.taskmigo.identity.group.hierarchy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupHierarchyTest {

    private static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LEFT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RIGHT = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID LEAF = UUID.fromString("00000000-0000-0000-0000-000000000004");

    /**
     * Verifies that graph traversal removes duplicate paths and remains deterministic.
     *
     * Given: a diamond-shaped Group hierarchy where two parents share one leaf.
     * Expect: each reachable Group appears exactly once in identifier order.
     */
    @Test
    @DisplayName("resolves reachable groups once in deterministic order")
    void shouldResolveReachableGroupsOnceWhenHierarchyHasSharedPaths() {
        // Arrange
        GroupHierarchy hierarchy = GroupHierarchy.from(
            Map.of(ROOT, List.of(RIGHT, LEFT), LEFT, List.of(LEAF), RIGHT, List.of(LEAF))
        );

        // Act + Assert
        assertThat(hierarchy.reachableFrom(ROOT)).containsExactly(ROOT, LEFT, RIGHT, LEAF);
    }

    /**
     * Verifies that traversal remains bounded even if persisted data already contains a cycle.
     *
     * Given: a Group graph with ROOT -> LEFT -> ROOT.
     * Expect: traversal returns each reachable Group once and terminates.
     */
    @Test
    @DisplayName("terminates traversal for cyclic persisted data")
    void shouldTerminateTraversalWhenPersistedDataIsCyclic() {
        // Arrange
        GroupHierarchy hierarchy = GroupHierarchy.from(Map.of(ROOT, List.of(LEFT), LEFT, List.of(ROOT)));

        // Act + Assert
        assertThat(hierarchy.reachableFrom(ROOT)).containsExactly(ROOT, LEFT);
    }

    /**
     * Verifies that mutation validation rejects an indirect cycle.
     *
     * Given: ROOT -> LEFT -> LEAF.
     * Expect: replacing LEAF children with ROOT raises a GroupHierarchyException.
     */
    @Test
    @DisplayName("rejects a replacement that creates an indirect cycle")
    void shouldRejectReplacementWhenItCreatesAnIndirectCycle() {
        // Arrange
        GroupHierarchy hierarchy = GroupHierarchy.from(Map.of(ROOT, List.of(LEFT), LEFT, List.of(LEAF)));

        // Act + Assert
        assertThatThrownBy(() -> hierarchy.replacingChildren(LEAF, List.of(ROOT)))
            .isInstanceOf(GroupHierarchyException.class)
            .hasMessage("Group hierarchy must be acyclic");
    }

    /**
     * Verifies that a Group cannot directly inherit from itself.
     *
     * Given: a graph containing ROOT with no children.
     * Expect: replacing ROOT children with ROOT raises a GroupHierarchyException.
     */
    @Test
    @DisplayName("rejects a self cycle")
    void shouldRejectReplacementWhenItCreatesASelfCycle() {
        // Arrange
        GroupHierarchy hierarchy = GroupHierarchy.from(Map.of(ROOT, List.of()));

        // Act + Assert
        assertThatThrownBy(() -> hierarchy.replacingChildren(ROOT, List.of(ROOT)))
            .isInstanceOf(GroupHierarchyException.class)
            .hasMessage("Group hierarchy must be acyclic");
    }

    /**
     * Verifies that a two-node cycle is rejected.
     *
     * Given: ROOT directly contains LEFT.
     * Expect: making LEFT contain ROOT raises a GroupHierarchyException.
     */
    @Test
    @DisplayName("rejects a direct cycle")
    void shouldRejectReplacementWhenItCreatesADirectCycle() {
        // Arrange
        GroupHierarchy hierarchy = GroupHierarchy.from(Map.of(ROOT, List.of(LEFT)));

        // Act + Assert
        assertThatThrownBy(() -> hierarchy.replacingChildren(LEFT, List.of(ROOT)))
            .isInstanceOf(GroupHierarchyException.class)
            .hasMessage("Group hierarchy must be acyclic");
    }

    /**
     * Verifies that replacing one Group's children preserves unrelated edges.
     *
     * Given: ROOT -> LEFT and LEFT -> LEAF.
     * Expect: replacing ROOT children with RIGHT changes only ROOT's outgoing edges.
     */
    @Test
    @DisplayName("replaces children without changing other edges")
    void shouldReplaceChildrenWhenNewChildrenAreProvided() {
        // Arrange
        GroupHierarchy hierarchy = GroupHierarchy.from(Map.of(ROOT, List.of(LEFT), LEFT, List.of(LEAF)));

        // Act
        GroupHierarchy replaced = hierarchy.replacingChildren(ROOT, List.of(RIGHT, RIGHT));

        // Assert
        assertThat(replaced.reachableFrom(ROOT)).containsExactly(ROOT, RIGHT);
        assertThat(replaced.reachableFrom(LEFT)).containsExactly(LEFT, LEAF);
    }

    /**
     * Verifies that an unknown root does not manufacture a graph node.
     *
     * Given: a graph containing only ROOT.
     * Expect: traversal from LEFT returns no Groups.
     */
    @Test
    @DisplayName("returns no groups for an unknown root")
    void shouldReturnNoGroupsWhenRootIsUnknown() {
        // Arrange
        GroupHierarchy hierarchy = GroupHierarchy.from(Map.of(ROOT, List.of()));

        // Act + Assert
        assertThat(hierarchy.reachableFrom(LEFT)).isEmpty();
    }
}
