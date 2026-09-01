package io.taskmigo.access;

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

    @Test
    @DisplayName("resolves transitive descendants once in deterministic order")
    void shouldResolveTransitiveDescendantsOnceWhenHierarchyHasSharedPaths() {
        RoleHierarchy hierarchy = new RoleHierarchy(
            Map.of(ROOT, List.of(RIGHT, LEFT), LEFT, List.of(LEAF), RIGHT, List.of(LEAF))
        );

        assertThat(hierarchy.descendants(ROOT)).containsExactly(LEFT, RIGHT, LEAF);
    }

    @Test
    @DisplayName("terminates traversal for cyclic persisted data")
    void shouldTerminateTraversalWhenPersistedDataIsCyclic() {
        RoleHierarchy hierarchy = new RoleHierarchy(Map.of(ROOT, List.of(LEFT), LEFT, List.of(ROOT)));

        assertThat(hierarchy.descendants(ROOT)).containsExactly(LEFT);
    }

    @Test
    @DisplayName("rejects a replacement that creates an indirect cycle")
    void shouldRejectReplacementWhenItCreatesAnIndirectCycle() {
        RoleHierarchy hierarchy = new RoleHierarchy(Map.of(ROOT, List.of(LEFT), LEFT, List.of(LEAF)));

        assertThatThrownBy(() -> hierarchy.replacingChildren(LEAF, List.of(ROOT)))
            .isInstanceOf(AccessException.class)
            .hasMessage("Role hierarchy must be acyclic");
    }

    @Test
    @DisplayName("rejects a self cycle")
    void shouldRejectReplacementWhenItCreatesASelfCycle() {
        RoleHierarchy hierarchy = new RoleHierarchy(Map.of(ROOT, List.of()));

        assertThatThrownBy(() -> hierarchy.replacingChildren(ROOT, List.of(ROOT)))
            .isInstanceOf(AccessException.class)
            .hasMessage("Role hierarchy must be acyclic");
    }

    @Test
    @DisplayName("rejects a direct cycle")
    void shouldRejectReplacementWhenItCreatesADirectCycle() {
        RoleHierarchy hierarchy = new RoleHierarchy(Map.of(ROOT, List.of(LEFT)));

        assertThatThrownBy(() -> hierarchy.replacingChildren(LEFT, List.of(ROOT)))
            .isInstanceOf(AccessException.class)
            .hasMessage("Role hierarchy must be acyclic");
    }

    @Test
    @DisplayName("replaces children without changing other edges")
    void shouldReplaceChildrenWhenNewChildrenAreProvided() {
        RoleHierarchy hierarchy = new RoleHierarchy(Map.of(ROOT, List.of(LEFT), LEFT, List.of(LEAF)));

        RoleHierarchy replaced = hierarchy.replacingChildren(ROOT, List.of(RIGHT, RIGHT));

        assertThat(replaced.descendants(ROOT)).containsExactly(RIGHT);
        assertThat(replaced.descendants(LEFT)).containsExactly(LEAF);
    }

    @Test
    @DisplayName("resolves multiple roots and shared descendants once")
    void shouldResolveSharedDescendantsOnceWhenMultipleRootsAreProvided() {
        RoleHierarchy hierarchy = new RoleHierarchy(
            Map.of(ROOT, List.of(LEFT), LEFT, List.of(LEAF), RIGHT, List.of(LEAF))
        );

        assertThat(hierarchy.reachableFrom(List.of(RIGHT, ROOT))).containsExactly(ROOT, LEFT, RIGHT, LEAF);
    }

    @Test
    @DisplayName("returns no descendants for an unknown root")
    void shouldReturnNoDescendantsWhenRootIsUnknown() {
        RoleHierarchy hierarchy = new RoleHierarchy(Map.of(ROOT, List.of()));

        assertThat(hierarchy.descendants(LEFT)).isEmpty();
    }
}
