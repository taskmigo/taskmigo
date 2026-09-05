package io.taskmigo.auth.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HierarchyClosureWriterTest {

    private static final UUID ROOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID LEAF_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final EntityManager entityManager = mock(EntityManager.class);
    private final HierarchyClosureWriter writer = new HierarchyClosureWriter(this.entityManager);

    /**
     * Verifies that closure rebuilding persists reflexive-transitive rows once per relationship.
     *
     * Given: three nodes whose reachable results contain a shared descendant and a duplicate row.
     * Expect: self-rows, multi-level rows, and shared rows are persisted once after clearing the table.
     */
    @Test
    @DisplayName("rebuilds a deduplicated reflexive transitive closure")
    void shouldPersistUniqueClosureRowsWhenReachabilityContainsSharedDescendants() {
        // Arrange
        List<TestNode> nodes = List.of(
            new TestNode(ROOT_ID),
            new TestNode(CHILD_ID),
            new TestNode(LEAF_ID),
            new TestNode(ROOT_ID)
        );
        Map<UUID, List<UUID>> reachable = Map.of(
            ROOT_ID,
            List.of(ROOT_ID, CHILD_ID, LEAF_ID, LEAF_ID),
            CHILD_ID,
            List.of(CHILD_ID, LEAF_ID, LEAF_ID),
            LEAF_ID,
            List.of(LEAF_ID)
        );
        Query deleteQuery = mock(Query.class);
        when(this.entityManager.createQuery("delete from TestClosure")).thenReturn(deleteQuery);
        ArgumentCaptor<TestClosure> rows = ArgumentCaptor.forClass(TestClosure.class);

        // Act
        this.writer.replace(
            nodes,
            TestNode::id,
            id -> Objects.requireNonNull(reachable.get(id)),
            TestClosure::new,
            TestClosure.class
        );

        // Assert
        verify(this.entityManager).createQuery("delete from TestClosure");
        verify(this.entityManager).clear();
        verify(this.entityManager, times(6)).persist(rows.capture());
        assertThat(rows.getAllValues())
            .extracting(TestClosure::ancestorId, TestClosure::descendantId)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(ROOT_ID, ROOT_ID),
                org.assertj.core.groups.Tuple.tuple(ROOT_ID, CHILD_ID),
                org.assertj.core.groups.Tuple.tuple(ROOT_ID, LEAF_ID),
                org.assertj.core.groups.Tuple.tuple(CHILD_ID, CHILD_ID),
                org.assertj.core.groups.Tuple.tuple(CHILD_ID, LEAF_ID),
                org.assertj.core.groups.Tuple.tuple(LEAF_ID, LEAF_ID)
            );
        verify(this.entityManager, times(2)).flush();
    }

    private record TestNode(UUID id) {}

    private record TestClosure(UUID ancestorId, UUID descendantId) {}
}
