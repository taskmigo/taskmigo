package io.taskmigo.authorization.role.adapter.out.persistence;

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
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoleHierarchyClosureWriterTest {

    private static final UUID ROOT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID LEAF_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final EntityManager entityManager = mock(EntityManager.class);
    private final RoleHierarchyClosureWriter writer = new RoleHierarchyClosureWriter(this.entityManager);

    /**
     * Verifies closure rebuilds deduplicate repeated nodes and reachable descendants.
     *
     * Given: a three-node hierarchy with a duplicate root and repeated descendant reachability.
     * Expect: exactly the six unique reflexive-transitive closure rows are persisted after the old closure is cleared.
     */
    @Test
    @DisplayName("rebuilds a deduplicated reflexive transitive closure")
    void shouldPersistUniqueClosureRowsWhenReachabilityContainsSharedDescendants() {
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

        this.writer.replace(
            nodes,
            TestNode::id,
            id -> Objects.requireNonNull(reachable.get(id)),
            TestClosure::new,
            TestClosure.class
        );

        verify(this.entityManager).createQuery("delete from TestClosure");
        verify(this.entityManager).clear();
        verify(this.entityManager, times(6)).persist(rows.capture());
        assertThat(rows.getAllValues())
            .extracting(TestClosure::ancestorId, TestClosure::descendantId)
            .containsExactlyInAnyOrder(
                Tuple.tuple(ROOT_ID, ROOT_ID),
                Tuple.tuple(ROOT_ID, CHILD_ID),
                Tuple.tuple(ROOT_ID, LEAF_ID),
                Tuple.tuple(CHILD_ID, CHILD_ID),
                Tuple.tuple(CHILD_ID, LEAF_ID),
                Tuple.tuple(LEAF_ID, LEAF_ID)
            );
        verify(this.entityManager, times(2)).flush();
    }

    private record TestNode(UUID id) {}

    private record TestClosure(UUID ancestorId, UUID descendantId) {}
}
