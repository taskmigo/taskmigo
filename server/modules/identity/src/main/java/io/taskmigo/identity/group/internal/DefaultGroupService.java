package io.taskmigo.identity.group.internal;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.GroupException;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.identity.group.GroupService;
import io.taskmigo.identity.group.hierarchy.GroupHierarchy;
import io.taskmigo.identity.group.hierarchy.GroupHierarchyException;
import io.taskmigo.identity.group.internal.GroupStore.GroupState;
import io.taskmigo.identity.user.UserService;
import io.taskmigo.query.QueryPredicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Implements Group use cases independently from the JPA adapter.
@Service
public class DefaultGroupService implements GroupService {

    private final GroupStore groups;
    private final UserService users;
    private final SubjectGrantService grants;

    public DefaultGroupService(GroupStore groups, UserService users, SubjectGrantService grants) {
        this.groups = groups;
        this.users = users;
        this.grants = grants;
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetPage<GroupInfo> list(
        int page,
        int perPage,
        QueryPredicate<GroupInfo> filter,
        ObjectAuthorizationPredicate<GroupInfo> authorization
    ) {
        return this.groups.list(page, perPage, filter, authorization);
    }

    @Override
    @Transactional
    public UUID create(@Nullable String name, @Nullable String description) {
        return this.create(name, description, Set.of(), Set.of());
    }

    @Override
    @Transactional
    public UUID create(
        @Nullable String name,
        @Nullable String description,
        @Nullable Collection<UUID> childGroupIds,
        @Nullable Collection<UUID> roleIds
    ) {
        UUID id = UUID.randomUUID();
        Set<UUID> requestedChildIds = childGroupIds == null ? Set.of() : Set.copyOf(childGroupIds);
        Collection<UUID> requestedRoleIds = roleIds == null ? Set.of() : roleIds;
        List<GroupState> allGroups = new ArrayList<>(this.groups.loadAllForUpdate());
        requireChildren(requestedChildIds, allGroups);

        GroupHierarchy hierarchy;
        try {
            hierarchy = hierarchy(allGroups).replacingChildren(id, requestedChildIds);
        } catch (GroupHierarchyException exception) {
            throw new GroupException(GroupException.Type.BAD_REQUEST, hierarchyFailureMessage(exception));
        }

        GroupState group = new GroupState(id, required(name, "name"), description, Set.of(), requestedChildIds);
        this.groups.create(group);
        allGroups.add(group);
        this.groups.replaceClosure(allGroups, hierarchy);
        this.grants.setRoles(IdentitySubjects.group(id), requestedRoleIds);
        return id;
    }

    @Override
    @Transactional
    public void addMember(UUID groupId, UUID userId) {
        requireGroup(this.groups.find(groupId));
        this.users.require(userId);
        this.groups.addMember(groupId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireGroups(Collection<UUID> ids) {
        Set<UUID> requestedIds = Set.copyOf(ids);
        if (!this.groups.containsAll(requestedIds)) {
            throw new GroupException(GroupException.Type.BAD_REQUEST, "One or more Groups do not exist");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> groupsForUser(UUID userId) {
        return this.groups.groupsForUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRolesForUser(UUID userId) {
        this.users.require(userId);
        LinkedHashSet<SubjectRef> subjects = new LinkedHashSet<>();
        subjects.add(IdentitySubjects.user(userId));
        List<UUID> directGroupIds = this.groups.groupsForUser(userId);
        if (!directGroupIds.isEmpty()) {
            this.groups
                .descendantGroupIds(directGroupIds)
                .forEach(groupId -> subjects.add(IdentitySubjects.group(groupId)));
        }
        return this.grants.effectiveRoles(subjects);
    }

    @Override
    @Transactional
    public void setChildGroups(UUID parentGroupId, Collection<UUID> childGroupIds) {
        Set<UUID> requestedIds = Set.copyOf(childGroupIds);
        List<GroupState> allGroups = new ArrayList<>(this.groups.loadAllForUpdate());
        GroupState parent = state(parentGroupId, allGroups);
        requireChildren(requestedIds, allGroups);

        GroupHierarchy hierarchy;
        try {
            hierarchy = hierarchy(allGroups).replacingChildren(parent.id(), requestedIds);
        } catch (GroupHierarchyException exception) {
            throw new GroupException(GroupException.Type.BAD_REQUEST, hierarchyFailureMessage(exception));
        }

        this.groups.replaceChildren(parent.id(), requestedIds);
        allGroups.replaceAll(group ->
            group.id().equals(parent.id())
                ? new GroupState(group.id(), group.name(), group.description(), group.memberIds(), requestedIds)
                : group
        );
        this.groups.replaceClosure(allGroups, hierarchy);
    }

    @Override
    @Transactional
    public void setRoles(UUID groupId, Collection<UUID> roleIds) {
        requireGroup(this.groups.find(groupId));
        this.grants.setRoles(IdentitySubjects.group(groupId), roleIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRoles(UUID groupId) {
        requireGroup(this.groups.find(groupId));
        LinkedHashSet<SubjectRef> subjects = new LinkedHashSet<>();
        this.groups.descendantGroupIds(Set.of(groupId)).forEach(id -> subjects.add(IdentitySubjects.group(id)));
        return this.grants.effectiveRoles(subjects);
    }

    private static GroupState state(UUID id, Collection<GroupState> groups) {
        return groups
            .stream()
            .filter(group -> group.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new GroupException(GroupException.Type.NOT_FOUND, "Group not found"));
    }

    private static GroupState requireGroup(Optional<GroupState> group) {
        return group.orElseThrow(() -> new GroupException(GroupException.Type.NOT_FOUND, "Group not found"));
    }

    private static void requireChildren(Set<UUID> childIds, Collection<GroupState> groups) {
        long found = groups.stream().map(GroupState::id).filter(childIds::contains).count();
        if (found != childIds.size()) {
            throw new GroupException(GroupException.Type.BAD_REQUEST, "One or more child Groups do not exist");
        }
    }

    private static GroupHierarchy hierarchy(Collection<GroupState> groups) {
        Map<UUID, Set<UUID>> children = groups.stream().collect(Collectors.toMap(GroupState::id, GroupState::childIds));
        return GroupHierarchy.from(children);
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new GroupException(GroupException.Type.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    private static String hierarchyFailureMessage(GroupHierarchyException exception) {
        String message = exception.getMessage();
        return message == null ? "Group hierarchy is invalid" : message;
    }
}
