package io.taskmigo.identity.provisioning.internal;

import io.taskmigo.authorization.subject.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.SubjectGrantQueryService;
import io.taskmigo.foundation.ReconciliationAction;
import io.taskmigo.foundation.ReconciliationResult;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.GroupException;
import io.taskmigo.identity.group.application.GroupCommandService;
import io.taskmigo.identity.group.application.GroupHierarchyRepository;
import io.taskmigo.identity.group.application.GroupMutationResult;
import io.taskmigo.identity.group.domain.Group;
import io.taskmigo.identity.group.domain.GroupRuleViolation;
import io.taskmigo.identity.group.domain.hierarchy.GroupHierarchy;
import io.taskmigo.identity.provisioning.GroupProvisioningService;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Reconciles managed Group profile and grants through the canonical Group command path.
@Service
class DefaultGroupProvisioningService implements GroupProvisioningService {

    private final GroupCommandService groups;
    private final GroupHierarchyRepository hierarchies;
    private final SubjectGrantAssignmentService grantAssignments;
    private final SubjectGrantQueryService grantQueries;

    DefaultGroupProvisioningService(
        GroupCommandService groups,
        GroupHierarchyRepository hierarchies,
        SubjectGrantAssignmentService grantAssignments,
        SubjectGrantQueryService grantQueries
    ) {
        this.groups = groups;
        this.hierarchies = hierarchies;
        this.grantAssignments = grantAssignments;
        this.grantQueries = grantQueries;
    }

    @Override
    @Transactional
    public ReconciliationResult<UUID> reconcileGroup(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> roleIds
    ) {
        Set<UUID> requestedRoleIds = Set.copyOf(roleIds);
        GroupMutationResult mutation;
        try {
            mutation = this.groups.reconcileManaged(code, displayName, description);
        } catch (GroupRuleViolation exception) {
            throw badRequest(exception);
        }

        UUID id = mutation.id();
        if (mutation.created()) {
            GroupHierarchy hierarchy = this.hierarchies.loadForMutation();
            this.hierarchies.synchronize(hierarchy);
            this.grantAssignments.setRoles(IdentitySubjects.group(id), requestedRoleIds);
            return new ReconciliationResult<>(id, ReconciliationAction.ADDED);
        }

        boolean rolesChanged = !this.grantQueries.roleIds(IdentitySubjects.group(id)).equals(requestedRoleIds);
        if (!mutation.changed() && !rolesChanged) {
            return new ReconciliationResult<>(id, ReconciliationAction.UNCHANGED);
        }
        if (rolesChanged) {
            this.grantAssignments.setRoles(IdentitySubjects.group(id), requestedRoleIds);
        }
        return new ReconciliationResult<>(id, ReconciliationAction.UPDATED);
    }

    @Override
    @Transactional
    public boolean deleteGroup(String code) {
        Group existing;
        try {
            existing = this.groups.findByCode(code).orElse(null);
        } catch (GroupRuleViolation exception) {
            throw badRequest(exception);
        }
        if (existing == null) {
            return false;
        }

        GroupHierarchy current = this.hierarchies.loadForMutation();
        GroupHierarchy remaining = current.removing(existing.id());
        this.grantAssignments.setRoles(IdentitySubjects.group(existing.id()), Set.of());
        this.hierarchies.remove(existing.id(), remaining);
        this.groups.delete(existing);
        return true;
    }

    private static GroupException badRequest(GroupRuleViolation exception) {
        return new GroupException(GroupException.Type.BAD_REQUEST, exception.detail());
    }
}
