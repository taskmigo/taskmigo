package io.taskmigo.identity.group.application.service;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.GroupException;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.identity.group.application.port.in.internal.GroupCommandService;
import io.taskmigo.identity.group.application.port.out.GroupHierarchyRepository;
import io.taskmigo.identity.group.application.port.out.GroupQueryRepository;
import io.taskmigo.identity.group.domain.GroupRuleViolation;
import io.taskmigo.identity.group.domain.hierarchy.GroupHierarchy;
import io.taskmigo.identity.group.domain.hierarchy.GroupHierarchyException;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Coordinates runtime Group profile, hierarchy, and grant-assignment use cases behind explicit ports.
public final class DefaultGroupService implements GroupService {

    private final GroupCommandService commands;
    private final GroupQueryRepository groups;
    private final GroupHierarchyRepository hierarchies;
    private final SubjectGrantAssignmentService grantAssignments;
    private final TransactionRunner transactions;

    public DefaultGroupService(
        GroupCommandService commands,
        GroupQueryRepository groups,
        GroupHierarchyRepository hierarchies,
        SubjectGrantAssignmentService grantAssignments,
        TransactionRunner transactions
    ) {
        this.commands = commands;
        this.groups = groups;
        this.hierarchies = hierarchies;
        this.grantAssignments = grantAssignments;
        this.transactions = transactions;
    }

    @Override
    public OffsetPage<GroupInfo> list(
        int page,
        int perPage,
        QueryPredicate<GroupInfo> filter,
        ObjectAuthorizationPredicate<GroupInfo> authorization
    ) {
        return this.transactions.read(() -> this.groups.list(page, perPage, filter, authorization));
    }

    @Override
    public UUID create(@Nullable String code, @Nullable String displayName, @Nullable String description) {
        return this.create(code, displayName, description, Set.of(), Set.of());
    }

    @Override
    public UUID create(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        @Nullable Collection<UUID> childGroupIds,
        @Nullable Collection<UUID> roleIds
    ) {
        return this.transactions.write(() -> {
            Set<UUID> requestedChildIds = childGroupIds == null ? Set.of() : Set.copyOf(childGroupIds);
            Set<UUID> requestedRoleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
            GroupHierarchy current = this.hierarchies.loadForMutation();
            requireChildren(requestedChildIds, current);

            UUID id;
            try {
                id = this.commands.createRuntime(code, displayName, description);
            } catch (GroupRuleViolation exception) {
                throw invalidInput(exception);
            }

            GroupHierarchy requested;
            try {
                requested = current.replacingChildren(id, requestedChildIds);
            } catch (GroupHierarchyException exception) {
                throw new GroupException(GroupException.Type.INVALID_INPUT, hierarchyFailureMessage(exception));
            }

            this.hierarchies.replaceChildren(id, requestedChildIds, requested);
            this.grantAssignments.setRoles(IdentitySubjects.group(id), requestedRoleIds);
            return id;
        });
    }

    @Override
    public void requireGroups(Collection<UUID> ids) {
        this.transactions.read(() -> {
            if (!this.groups.containsAll(Set.copyOf(ids))) {
                throw new GroupException(GroupException.Type.INVALID_INPUT, "One or more Groups do not exist");
            }
            return true;
        });
    }

    @Override
    public void setChildGroups(UUID parentGroupId, Collection<UUID> childGroupIds) {
        this.transactions.write(() -> {
            Set<UUID> requestedIds = Set.copyOf(childGroupIds);
            GroupHierarchy current = this.hierarchies.loadForMutation();
            requireGroup(parentGroupId, current);
            requireChildren(requestedIds, current);

            GroupHierarchy requested;
            try {
                requested = current.replacingChildren(parentGroupId, requestedIds);
            } catch (GroupHierarchyException exception) {
                throw new GroupException(GroupException.Type.INVALID_INPUT, hierarchyFailureMessage(exception));
            }

            this.hierarchies.replaceChildren(parentGroupId, requestedIds, requested);
        });
    }

    @Override
    public void setRoles(UUID groupId, Collection<UUID> roleIds) {
        this.transactions.write(() -> {
            this.requireGroup(groupId);
            this.grantAssignments.setRoles(IdentitySubjects.group(groupId), roleIds);
        });
    }

    private void requireGroup(UUID id) {
        if (!this.groups.exists(id)) {
            throw new GroupException(GroupException.Type.NOT_FOUND, "Group not found");
        }
    }

    private static void requireGroup(UUID id, GroupHierarchy hierarchy) {
        if (!hierarchy.contains(id)) {
            throw new GroupException(GroupException.Type.NOT_FOUND, "Group not found");
        }
    }

    private static void requireChildren(Set<UUID> childIds, GroupHierarchy hierarchy) {
        if (!hierarchy.containsAll(childIds)) {
            throw new GroupException(GroupException.Type.INVALID_INPUT, "One or more child Groups do not exist");
        }
    }

    private static GroupException invalidInput(GroupRuleViolation exception) {
        return new GroupException(GroupException.Type.INVALID_INPUT, exception.detail());
    }

    private static String hierarchyFailureMessage(GroupHierarchyException exception) {
        String message = exception.getMessage();
        return message == null ? "Group hierarchy is invalid" : message;
    }
}
