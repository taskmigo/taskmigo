package io.taskmigo.identity.persistence.group;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/// Rebuilds the Identity-owned Group hierarchy closure inside the caller's mutation transaction.
@Service
public final class GroupHierarchyClosureWriter {

    private final EntityManager entityManager;

    public GroupHierarchyClosureWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void replace(Collection<GroupEntity> groups, Function<UUID, ? extends Collection<UUID>> reachable) {
        LinkedHashSet<UUID> ancestorIds = new LinkedHashSet<>();
        for (GroupEntity group : groups) {
            ancestorIds.add(group.id());
        }
        this.entityManager.flush();
        this.entityManager.createQuery("delete from GroupHierarchyClosureEntity").executeUpdate();
        this.entityManager.clear();
        for (UUID ancestorId : ancestorIds) {
            for (UUID descendantId : new LinkedHashSet<>(reachable.apply(ancestorId))) {
                this.entityManager.persist(new GroupHierarchyClosureEntity(ancestorId, descendantId));
            }
        }
        this.entityManager.flush();
    }
}
