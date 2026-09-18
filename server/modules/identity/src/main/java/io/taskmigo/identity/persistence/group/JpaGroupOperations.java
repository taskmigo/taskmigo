package io.taskmigo.identity.persistence.group;

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
import io.taskmigo.identity.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.user.UserService;
import io.taskmigo.query.QueryPredicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages global groups and their memberships while delegating authorization grants to Access Control.
@Service
public class JpaGroupOperations implements GroupService {

    private final GroupRepository groups;
    private final GroupHierarchyClosureWriter closureWriter;
    private final UserService users;
    private final SubjectGrantService grants;
    private final QueryPredicateBinder<GroupInfo, GroupEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<GroupInfo, GroupEntity> objectBinder;

    public JpaGroupOperations(
        GroupRepository groups,
        GroupHierarchyClosureWriter closureWriter,
        UserService users,
        SubjectGrantService grants,
        QueryPredicateBinder<GroupInfo, GroupEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<GroupInfo, GroupEntity> objectBinder
    ) {
        this.groups = groups;
        this.closureWriter = closureWriter;
        this.users = users;
        this.grants = grants;
        this.queryBinder = queryBinder;
        this.objectBinder = objectBinder;
    }

    /// Lists Groups by binding both opaque predicates before pagination.
    @Transactional(readOnly = true)
    public OffsetPage<GroupInfo> list(
        int page,
        int perPage,
        QueryPredicate<GroupInfo> filter,
        ObjectAuthorizationPredicate<GroupInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var groups = this.groups.findAll(
            this.queryBinder.bind(filter).and(this.objectBinder.bind(authorization)),
            pageable
        );
        return new OffsetPage<>(
            groups.map(JpaGroupOperations::info).getContent(),
            groups.getTotalElements(),
            groups.getTotalPages()
        );
    }

    @Transactional
    public UUID create(@Nullable String name, @Nullable String description) {
        return this.create(name, description, Set.of(), Set.of());
    }

    /// Creates a Group and its direct child-Group relationships and Access Control Role bindings atomically.
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
        List<GroupEntity> allGroups = new ArrayList<>(this.groups.findAllForUpdate());
        List<GroupEntity> children = requireChildGroups(requestedChildIds, allGroups);
        try {
            hierarchy(allGroups).replacingChildren(id, requestedChildIds);
        } catch (GroupHierarchyException exception) {
            throw new GroupException(GroupException.Type.BAD_REQUEST, hierarchyFailureMessage(exception));
        }

        GroupEntity group = new GroupEntity(id, required(name, "name"), description);
        group.addChildGroups(children);
        this.groups.save(group);
        allGroups.add(group);
        this.groups.flush();
        this.refreshClosure(allGroups);
        this.grants.setRoles(IdentitySubjects.group(id), requestedRoleIds);
        return id;
    }

    @Transactional
    public void addMember(UUID groupId, UUID userId) {
        GroupEntity group = this.entity(groupId);
        this.users.require(userId);
        group.addMember(userId);
        this.groups.flush();
    }

    /// Validates that every supplied Group id exists.
    @Transactional(readOnly = true)
    public void requireGroups(Collection<UUID> ids) {
        Set<UUID> requestedIds = Set.copyOf(ids);
        if (this.groups.findAllById(requestedIds).size() != requestedIds.size()) {
            throw new GroupException(GroupException.Type.BAD_REQUEST, "One or more Groups do not exist");
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> groupsForUser(UUID userId) {
        return this.groups.findDistinctByMemberIdsContains(userId).stream().map(GroupEntity::id).toList();
    }

    /// Resolves a User's direct and Group-derived Roles through Access Control subject bindings.
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRolesForUser(UUID userId) {
        this.users.require(userId);
        LinkedHashSet<SubjectRef> subjects = new LinkedHashSet<>();
        subjects.add(IdentitySubjects.user(userId));
        List<UUID> directGroupIds = this.groupsForUser(userId);
        if (!directGroupIds.isEmpty()) {
            this.groups
                .findDescendantGroupIds(directGroupIds)
                .forEach(groupId -> subjects.add(IdentitySubjects.group(groupId)));
        }
        return this.grants.effectiveRoles(subjects);
    }

    /// Replaces a Group's direct child Groups after validating the resulting global graph.
    @Transactional
    public void setChildGroups(UUID parentGroupId, Collection<UUID> childGroupIds) {
        Set<UUID> requestedIds = Set.copyOf(childGroupIds);
        List<GroupEntity> allGroups = this.groups.findAllForUpdate();
        GroupEntity parent = entity(parentGroupId, allGroups);
        List<GroupEntity> children = requireChildGroups(requestedIds, allGroups);

        try {
            hierarchy(allGroups).replacingChildren(parent.id(), requestedIds);
        } catch (GroupHierarchyException exception) {
            throw new GroupException(GroupException.Type.BAD_REQUEST, hierarchyFailureMessage(exception));
        }
        parent.replaceChildGroups(children);
        this.groups.flush();
        this.refreshClosure(allGroups);
    }

    /// Replaces the Roles directly bound to a Group through Access Control.
    @Transactional
    public void setRoles(UUID groupId, Collection<UUID> roleIds) {
        this.entity(groupId);
        this.grants.setRoles(IdentitySubjects.group(groupId), roleIds);
    }

    /// Resolves Roles bound to a Group or any descendant Group, including inherited descendant Roles.
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRoles(UUID groupId) {
        this.entity(groupId);
        LinkedHashSet<SubjectRef> subjects = new LinkedHashSet<>();
        this.groups.findDescendantGroupIds(Set.of(groupId)).forEach(id -> subjects.add(IdentitySubjects.group(id)));
        return this.grants.effectiveRoles(subjects);
    }

    private GroupEntity entity(UUID id) {
        return this.groups
            .findById(id)
            .orElseThrow(() -> new GroupException(GroupException.Type.NOT_FOUND, "Group not found"));
    }

    private static GroupEntity entity(UUID id, Collection<GroupEntity> groups) {
        return groups
            .stream()
            .filter(group -> group.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new GroupException(GroupException.Type.NOT_FOUND, "Group not found"));
    }

    private static List<GroupEntity> requireChildGroups(Set<UUID> childGroupIds, Collection<GroupEntity> groups) {
        List<GroupEntity> children = groups
            .stream()
            .filter(group -> childGroupIds.contains(group.id()))
            .toList();
        if (children.size() != childGroupIds.size()) {
            throw new GroupException(GroupException.Type.BAD_REQUEST, "One or more child Groups do not exist");
        }
        return children;
    }

    private void refreshClosure(Collection<GroupEntity> allGroups) {
        GroupHierarchy hierarchy = hierarchy(allGroups);
        this.closureWriter.replace(allGroups, groupId -> hierarchy.reachableFrom(Set.of(groupId)));
    }

    private static GroupHierarchy hierarchy(Collection<GroupEntity> groups) {
        return GroupHierarchy.from(
            groups
                .stream()
                .collect(
                    Collectors.toMap(GroupEntity::id, group ->
                        group.childGroups().stream().map(GroupEntity::id).collect(Collectors.toSet())
                    )
                )
        );
    }

    private static GroupInfo info(GroupEntity group) {
        return info(group, Set.of());
    }

    private static GroupInfo info(GroupEntity group, Set<UUID> ancestors) {
        if (ancestors.contains(group.id())) {
            return new GroupInfo(group.id(), group.name(), group.description(), List.of());
        }
        Set<UUID> nextAncestors = new HashSet<>(ancestors);
        nextAncestors.add(group.id());
        List<GroupInfo> children = group
            .childGroups()
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .map(child -> info(child, nextAncestors))
            .toList();
        return new GroupInfo(group.id(), group.name(), group.description(), children);
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
