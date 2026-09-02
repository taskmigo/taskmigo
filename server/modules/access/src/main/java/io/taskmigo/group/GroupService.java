package io.taskmigo.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.taskmigo.access.AccessService;
import io.taskmigo.access.AccessService.RoleInfo;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.user.UserService;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages global groups and their memberships.
@Service
public class GroupService {

    private final GroupRepository groups;
    private final UserService users;
    private final AccessService access;

    GroupService(GroupRepository groups, UserService users, AccessService access) {
        this.groups = groups;
        this.users = users;
        this.access = access;
    }

    /// Lists Groups in stable id order, retaining their direct child hierarchy in each returned item.
    ///
    /// @param page the one-based page requested by the API client
    /// @param perPage the maximum number of Groups to return
    /// @return one offset-paginated page of Groups
    @Transactional(readOnly = true)
    public OffsetPage<GroupInfo> list(int page, int perPage) {
        var groups = this.groups.findAll(PageRequest.of(page - 1, perPage, Sort.by("id")));
        return new OffsetPage<>(
            groups.map(GroupService::info).getContent(),
            groups.getTotalElements(),
            groups.getTotalPages()
        );
    }

    @Transactional
    public UUID create(@Nullable String name, @Nullable String description) {
        return this.create(name, description, Set.of(), Set.of());
    }

    /// Creates a Group and its direct child-Group and Role relationships as one atomic operation.
    ///
    /// Duplicate ids are normalized. Creation fails without persisting the Group when a child Group or Role does not
    /// exist or the resulting Group graph would be cyclic.
    ///
    /// @param name the display name of the Group
    /// @param description the optional explanation of the Group's purpose
    /// @param childGroupIds the optional direct child Groups inherited by the new Group
    /// @param roleIds the optional Roles directly included by the new Group
    /// @return the id of the created Group
    @Transactional
    public UUID create(
        @Nullable String name,
        @Nullable String description,
        @Nullable Collection<UUID> childGroupIds,
        @Nullable Collection<UUID> roleIds
    ) {
        UUID id = UUID.randomUUID();
        Set<UUID> requestedChildIds = childGroupIds == null ? Set.of() : Set.copyOf(childGroupIds);
        Set<UUID> requestedRoleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        List<GroupEntity> allGroups = this.groups.findAllForUpdate();
        List<GroupEntity> children = requireChildGroups(requestedChildIds, allGroups);
        GroupHierarchy.from(allGroups).replacingChildren(id, requestedChildIds);
        this.access.requireRoles(requestedRoleIds);

        GroupEntity group = new GroupEntity(id, required(name, "name"), description);
        group.childGroups.addAll(children);
        group.roleIds.addAll(requestedRoleIds);
        this.groups.save(group);
        return id;
    }

    @Transactional
    public void addMember(UUID groupId, UUID userId) {
        GroupEntity group = this.entity(groupId);
        this.users.require(userId);
        group.memberIds.add(userId);
        this.groups.flush();
    }

    @Transactional
    public void removeMember(UUID groupId, UUID userId) {
        GroupEntity group = this.entity(groupId);
        group.memberIds.remove(userId);
        this.groups.flush();
    }

    @Transactional(readOnly = true)
    public GroupInfo require(UUID id) {
        return info(this.entity(id));
    }

    /// Validates that every supplied Group id exists.
    ///
    /// @param ids the Group ids to validate
    @Transactional(readOnly = true)
    public void requireGroups(Collection<UUID> ids) {
        Set<UUID> requestedIds = Set.copyOf(ids);
        if (this.groups.findAllById(requestedIds).size() != requestedIds.size()) throw new GroupException(
            GroupException.Type.BAD_REQUEST,
            "One or more Groups do not exist"
        );
    }

    @Transactional(readOnly = true)
    public List<UUID> groupsForUser(UUID userId) {
        return this.groups
            .findAllByMemberIdsContains(userId)
            .stream()
            .map(group -> group.id)
            .toList();
    }

    /// Resolves a User's direct and Group-derived Roles, including every inherited descendant Role.
    ///
    /// @param userId the User whose effective Roles are resolved
    /// @return the deduplicated effective Roles in stable id order
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRolesForUser(UUID userId) {
        Set<UUID> roleIds = new HashSet<>(this.users.roleIds(userId));
        for (UUID groupId : this.groupsForUser(userId)) {
            this.effectiveRoles(groupId).forEach(role -> roleIds.add(role.id()));
        }
        return this.access.effectiveRoles(roleIds);
    }

    /// Replaces a Group's direct child Groups after validating the resulting global graph.
    ///
    /// Duplicate child ids are normalized. The replacement is rejected before persistence when a child does not
    /// exist or would make the Group graph cyclic. Concurrent hierarchy writers are serialized before validation.
    ///
    /// @param parentGroupId the Group whose outgoing hierarchy edges are replaced
    /// @param childGroupIds the complete desired set of direct child Groups
    @Transactional
    public void setChildGroups(UUID parentGroupId, Collection<UUID> childGroupIds) {
        Set<UUID> requestedIds = Set.copyOf(childGroupIds);
        List<GroupEntity> allGroups = this.groups.findAllForUpdate();
        GroupEntity parent = entity(parentGroupId, allGroups);
        List<GroupEntity> children = requireChildGroups(requestedIds, allGroups);

        GroupHierarchy.from(allGroups).replacingChildren(parent.id, requestedIds);
        parent.childGroups.clear();
        parent.childGroups.addAll(children);
        this.groups.flush();
    }

    /// Replaces the Roles directly included by a Group.
    ///
    /// Duplicate ids are normalized and the mutation is rejected before persistence when any Role does not exist.
    ///
    /// @param groupId the Group whose direct Roles are replaced
    /// @param roleIds the complete desired set of directly included Roles
    @Transactional
    public void setRoles(UUID groupId, Collection<UUID> roleIds) {
        GroupEntity group = this.entity(groupId);
        Set<UUID> requestedIds = Set.copyOf(roleIds);
        this.access.requireRoles(requestedIds);
        group.roleIds.clear();
        group.roleIds.addAll(requestedIds);
        this.groups.flush();
    }

    /// Resolves all Roles included by a Group or any descendant Group, including inherited descendant Roles.
    ///
    /// Every Role is returned once in deterministic id order. Traversal terminates if persisted hierarchy data is
    /// cyclic.
    ///
    /// @param groupId the Group at the root of the downward traversal
    /// @return all effective Roles for the Group
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRoles(UUID groupId) {
        GroupEntity root = this.entity(groupId);
        List<GroupEntity> allGroups = this.groups.findAll();
        Map<UUID, GroupEntity> groupsById = new HashMap<>();
        for (GroupEntity group : allGroups) groupsById.put(group.id, group);

        Set<UUID> roleIds = new HashSet<>();
        for (UUID reachableGroupId : GroupHierarchy.from(allGroups).reachableFrom(root.id)) {
            roleIds.addAll(Objects.requireNonNull(groupsById.get(reachableGroupId)).roleIds);
        }
        return this.access.effectiveRoles(roleIds);
    }

    public record GroupInfo(
        UUID id,
        String name,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String description,
        List<GroupInfo> children
    ) {}

    private GroupEntity entity(UUID id) {
        return this.groups
            .findById(id)
            .orElseThrow(() -> new GroupException(GroupException.Type.NOT_FOUND, "Group not found"));
    }

    private static GroupEntity entity(UUID id, Collection<GroupEntity> groups) {
        return groups
            .stream()
            .filter(group -> group.id.equals(id))
            .findFirst()
            .orElseThrow(() -> new GroupException(GroupException.Type.NOT_FOUND, "Group not found"));
    }

    private static List<GroupEntity> requireChildGroups(Set<UUID> childGroupIds, Collection<GroupEntity> groups) {
        List<GroupEntity> children = groups
            .stream()
            .filter(group -> childGroupIds.contains(group.id))
            .toList();
        if (children.size() != childGroupIds.size()) throw new GroupException(
            GroupException.Type.BAD_REQUEST,
            "One or more child Groups do not exist"
        );
        return children;
    }

    private static GroupInfo info(GroupEntity group) {
        return info(group, Set.of());
    }

    private static GroupInfo info(GroupEntity group, Set<UUID> ancestors) {
        if (ancestors.contains(group.id)) return new GroupInfo(group.id, group.name, group.description, List.of());
        Set<UUID> nextAncestors = new HashSet<>(ancestors);
        nextAncestors.add(group.id);
        List<GroupInfo> children = group.childGroups
            .stream()
            .sorted((left, right) -> left.id.compareTo(right.id))
            .map(child -> info(child, nextAncestors))
            .toList();
        return new GroupInfo(group.id, group.name, group.description, children);
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) throw new GroupException(
            GroupException.Type.BAD_REQUEST,
            field + " is required"
        );
        return value.trim();
    }
}
