package io.taskmigo.identity.persistence.group;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.identity.group.hierarchy.GroupHierarchy;
import io.taskmigo.identity.group.internal.GroupStore;
import io.taskmigo.identity.group.internal.GroupStore.GroupState;
import io.taskmigo.identity.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.QueryPredicateBinder;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/// Implements Group persistence operations with JPA entities and repositories.
@Service
public class JpaGroupOperations implements GroupStore {

    private final GroupRepository groups;
    private final GroupHierarchyClosureWriter closureWriter;
    private final QueryPredicateBinder<GroupInfo, GroupEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<GroupInfo, GroupEntity> objectBinder;

    public JpaGroupOperations(
        GroupRepository groups,
        GroupHierarchyClosureWriter closureWriter,
        QueryPredicateBinder<GroupInfo, GroupEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<GroupInfo, GroupEntity> objectBinder
    ) {
        this.groups = groups;
        this.closureWriter = closureWriter;
        this.queryBinder = queryBinder;
        this.objectBinder = objectBinder;
    }

    @Override
    public List<GroupState> loadAllForUpdate() {
        return this.groups.findAllForUpdate().stream().map(JpaGroupOperations::state).toList();
    }

    @Override
    public Optional<GroupState> find(UUID id) {
        return this.groups.findById(id).map(JpaGroupOperations::state);
    }

    @Override
    public Optional<GroupState> findByCode(String code) {
        return this.groups.findByCode(code).map(JpaGroupOperations::state);
    }

    @Override
    public boolean containsAll(Collection<UUID> ids) {
        return this.groups.findAllById(ids).size() == ids.size();
    }

    @Override
    public void create(GroupState group) {
        List<GroupEntity> children = this.groups.findDistinctByIdIn(group.childIds());
        GroupEntity entity = new GroupEntity(group.id(), group.code(), group.displayName(), group.description());
        entity.addChildGroups(children);
        for (UUID memberId : group.memberIds()) {
            entity.addMember(memberId);
        }
        this.groups.saveAndFlush(entity);
    }

    @Override
    public void updateDisplayNameAndDescription(UUID groupId, String displayName, @Nullable String description) {
        GroupEntity group = this.groups.findById(groupId).orElseThrow();
        group.updateDisplayNameAndDescription(displayName, description);
        this.groups.flush();
    }

    @Override
    public void addMember(UUID groupId, UUID userId) {
        GroupEntity group = this.groups.findById(groupId).orElseThrow();
        group.addMember(userId);
        this.groups.flush();
    }

    @Override
    public void replaceMemberships(UUID userId, Set<UUID> groupIds) {
        List<GroupEntity> allGroups = this.groups.findAllForUpdate();
        for (GroupEntity group : allGroups) {
            Set<UUID> memberIds = group.memberIds();
            if (groupIds.contains(group.id())) {
                memberIds = new LinkedHashSet<>(memberIds);
                memberIds.add(userId);
            } else {
                memberIds = new LinkedHashSet<>(memberIds);
                memberIds.remove(userId);
            }
            group.replaceMembers(memberIds);
        }
        this.groups.flush();
    }

    @Override
    public void delete(UUID groupId) {
        this.groups.deleteById(groupId);
        this.groups.flush();
    }

    @Override
    public List<UUID> groupsForUser(UUID userId) {
        return this.groups.findDistinctByMemberIdsContains(userId).stream().map(GroupEntity::id).toList();
    }

    @Override
    public List<UUID> descendantGroupIds(Collection<UUID> ancestorGroupIds) {
        return this.groups.findDescendantGroupIds(ancestorGroupIds);
    }

    @Override
    public void replaceChildren(UUID groupId, Set<UUID> childIds) {
        GroupEntity group = this.groups.findById(groupId).orElseThrow();
        group.replaceChildGroups(this.groups.findDistinctByIdIn(childIds));
        this.groups.flush();
    }

    @Override
    public void replaceClosure(Collection<GroupState> states, GroupHierarchy hierarchy) {
        List<GroupEntity> entities = this.groups.findDistinctByIdIn(states.stream().map(GroupState::id).toList());
        this.closureWriter.replace(entities, groupId -> hierarchy.reachableFrom(Set.of(groupId)));
    }

    @Override
    public OffsetPage<GroupInfo> list(
        int page,
        int perPage,
        QueryPredicate<GroupInfo> filter,
        ObjectAuthorizationPredicate<GroupInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var result = this.groups.findAll(
            this.queryBinder.bind(filter).and(this.objectBinder.bind(authorization)),
            pageable
        );
        return new OffsetPage<>(
            result.map(JpaGroupOperations::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    private static GroupInfo info(GroupEntity group) {
        return info(group, Set.of());
    }

    private static GroupInfo info(GroupEntity group, Set<UUID> ancestors) {
        if (ancestors.contains(group.id())) {
            return new GroupInfo(group.id(), group.code(), group.displayName(), group.description(), List.of());
        }
        Set<UUID> nextAncestors = new HashSet<>(ancestors);
        nextAncestors.add(group.id());
        List<GroupInfo> children = group
            .childGroups()
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .map(child -> info(child, nextAncestors))
            .toList();
        return new GroupInfo(group.id(), group.code(), group.displayName(), group.description(), children);
    }

    private static GroupState state(GroupEntity group) {
        return new GroupState(
            group.id(),
            group.code(),
            group.displayName(),
            group.description(),
            group.memberIds(),
            group.childGroups().stream().map(GroupEntity::id).collect(Collectors.toSet())
        );
    }
}
