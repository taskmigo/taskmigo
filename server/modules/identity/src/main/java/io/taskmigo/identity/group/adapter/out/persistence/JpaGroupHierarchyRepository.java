package io.taskmigo.identity.group.adapter.out.persistence;

import io.taskmigo.identity.group.application.port.out.GroupHierarchyRepository;
import io.taskmigo.identity.group.domain.hierarchy.GroupHierarchy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/// Adapts Group hierarchy persistence, concurrency control, derived closure maintenance, and descendant reads to JPA.
@Repository
public class JpaGroupHierarchyRepository implements GroupHierarchyRepository {

    private final JpaGroupRepository groups;
    private final GroupHierarchyClosureWriter closureWriter;

    JpaGroupHierarchyRepository(JpaGroupRepository groups, GroupHierarchyClosureWriter closureWriter) {
        this.groups = groups;
        this.closureWriter = closureWriter;
    }

    @Override
    public GroupHierarchy loadForMutation() {
        Map<UUID, Set<UUID>> childrenByParent = this.groups
            .findAllForUpdate()
            .stream()
            .collect(
                Collectors.toMap(GroupEntity::id, group ->
                    group.childGroups().stream().map(GroupEntity::id).collect(Collectors.toSet())
                )
            );
        return GroupHierarchy.from(childrenByParent);
    }

    @Override
    public void replaceChildren(UUID groupId, Set<UUID> childIds, GroupHierarchy hierarchy) {
        GroupEntity group = this.groups.findById(groupId).orElseThrow();
        group.replaceChildGroups(this.groups.findDistinctByIdIn(childIds));
        this.groups.flush();
        this.synchronize(hierarchy);
    }

    @Override
    public void synchronize(GroupHierarchy hierarchy) {
        this.closureWriter.replace(hierarchy.groupIds(), hierarchy::reachableFrom);
    }

    @Override
    public void remove(UUID groupId, GroupHierarchy hierarchy) {
        this.groups.findAllForUpdate().forEach(group -> {
            if (group.id().equals(groupId)) {
                if (!group.childGroups().isEmpty()) {
                    group.replaceChildGroups(Set.of());
                }
                return;
            }

            Set<GroupEntity> children = group.childGroups();
            if (children.stream().noneMatch(child -> child.id().equals(groupId))) {
                return;
            }
            group.replaceChildGroups(
                children
                    .stream()
                    .filter(child -> !child.id().equals(groupId))
                    .toList()
            );
        });
        this.groups.flush();
        this.synchronize(hierarchy);
    }

    @Override
    public List<UUID> descendantGroupIds(Collection<UUID> ancestorGroupIds) {
        return this.groups.findDescendantGroupIds(ancestorGroupIds);
    }
}
