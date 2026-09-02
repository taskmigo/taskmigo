package io.taskmigo.auth;

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

    @Test
    @DisplayName("resolves reachable groups once in deterministic order")
    void shouldResolveReachableGroupsOnceWhenHierarchyHasSharedPaths() {
        GroupHierarchy hierarchy = new GroupHierarchy(
            Map.of(ROOT, List.of(RIGHT, LEFT), LEFT, List.of(LEAF), RIGHT, List.of(LEAF))
        );

        assertThat(hierarchy.reachableFrom(ROOT)).containsExactly(ROOT, LEFT, RIGHT, LEAF);
    }

    @Test
    @DisplayName("terminates traversal for cyclic persisted data")
    void shouldTerminateTraversalWhenPersistedDataIsCyclic() {
        GroupHierarchy hierarchy = new GroupHierarchy(Map.of(ROOT, List.of(LEFT), LEFT, List.of(ROOT)));

        assertThat(hierarchy.reachableFrom(ROOT)).containsExactly(ROOT, LEFT);
    }

    @Test
    @DisplayName("rejects a replacement that creates an indirect cycle")
    void shouldRejectReplacementWhenItCreatesAnIndirectCycle() {
        GroupHierarchy hierarchy = new GroupHierarchy(Map.of(ROOT, List.of(LEFT), LEFT, List.of(LEAF)));

        assertThatThrownBy(() -> hierarchy.replacingChildren(LEAF, List.of(ROOT)))
            .isInstanceOf(GroupException.class)
            .hasMessage("Group hierarchy must be acyclic");
    }

    @Test
    @DisplayName("rejects a self cycle")
    void shouldRejectReplacementWhenItCreatesASelfCycle() {
        GroupHierarchy hierarchy = new GroupHierarchy(Map.of(ROOT, List.of()));

        assertThatThrownBy(() -> hierarchy.replacingChildren(ROOT, List.of(ROOT)))
            .isInstanceOf(GroupException.class)
            .hasMessage("Group hierarchy must be acyclic");
    }

    @Test
    @DisplayName("rejects a direct cycle")
    void shouldRejectReplacementWhenItCreatesADirectCycle() {
        GroupHierarchy hierarchy = new GroupHierarchy(Map.of(ROOT, List.of(LEFT)));

        assertThatThrownBy(() -> hierarchy.replacingChildren(LEFT, List.of(ROOT)))
            .isInstanceOf(GroupException.class)
            .hasMessage("Group hierarchy must be acyclic");
    }

    @Test
    @DisplayName("replaces children without changing other edges")
    void shouldReplaceChildrenWhenNewChildrenAreProvided() {
        GroupHierarchy hierarchy = new GroupHierarchy(Map.of(ROOT, List.of(LEFT), LEFT, List.of(LEAF)));

        GroupHierarchy replaced = hierarchy.replacingChildren(ROOT, List.of(RIGHT, RIGHT));

        assertThat(replaced.reachableFrom(ROOT)).containsExactly(ROOT, RIGHT);
        assertThat(replaced.reachableFrom(LEFT)).containsExactly(LEFT, LEAF);
    }

    @Test
    @DisplayName("returns no groups for an unknown root")
    void shouldReturnNoGroupsWhenRootIsUnknown() {
        GroupHierarchy hierarchy = new GroupHierarchy(Map.of(ROOT, List.of()));

        assertThat(hierarchy.reachableFrom(LEFT)).isEmpty();
    }
}
