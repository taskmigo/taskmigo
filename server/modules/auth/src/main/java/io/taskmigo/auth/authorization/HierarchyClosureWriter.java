package io.taskmigo.auth.authorization;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/// Rebuilds a hierarchy closure without making the closure table an authorization cache.
@Service
public final class HierarchyClosureWriter {

    private final EntityManager entityManager;

    /// Creates a writer backed by the current persistence context.
    public HierarchyClosureWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /// Replaces every row in one closure table with the supplied reflexive-transitive relations.
    ///
    /// The caller must invoke this method inside the hierarchy mutation transaction. The entity
    /// type is used only to resolve the fixed JPA entity name for the bulk delete; no entity or
    /// authorization state is retained by this component.
    ///
    /// @param nodes all hierarchy nodes to use as closure ancestors
    /// @param nodeId extracts a node's stable identifier
    /// @param reachable returns the reflexive-transitive descendants for an ancestor
    /// @param closureFactory creates a closure entity for one ancestor/descendant pair
    /// @param closureEntityType the JPA entity type whose table is being rebuilt
    public <N, C> void replace(
        Collection<N> nodes,
        Function<N, UUID> nodeId,
        Function<UUID, ? extends Collection<UUID>> reachable,
        BiFunction<UUID, UUID, C> closureFactory,
        Class<C> closureEntityType
    ) {
        LinkedHashSet<UUID> ancestorIds = new LinkedHashSet<>();
        for (N node : nodes) {
            ancestorIds.add(nodeId.apply(node));
        }
        this.entityManager.flush();
        this.entityManager.createQuery("delete from " + closureEntityType.getSimpleName()).executeUpdate();
        this.entityManager.clear();
        for (UUID ancestorId : ancestorIds) {
            for (UUID descendantId : new LinkedHashSet<>(reachable.apply(ancestorId))) {
                this.entityManager.persist(closureFactory.apply(ancestorId, descendantId));
            }
        }
        this.entityManager.flush();
    }
}
