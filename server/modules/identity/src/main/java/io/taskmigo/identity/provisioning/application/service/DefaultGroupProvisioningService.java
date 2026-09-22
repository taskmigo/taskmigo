package io.taskmigo.identity.provisioning.application.service;

import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.GroupException;
import io.taskmigo.identity.group.application.port.in.internal.GroupCommandService;
import io.taskmigo.identity.group.application.port.in.internal.GroupMutationResult;
import io.taskmigo.identity.group.application.port.out.GroupHierarchyRepository;
import io.taskmigo.identity.group.domain.Group;
import io.taskmigo.identity.group.domain.GroupRuleViolation;
import io.taskmigo.identity.group.domain.hierarchy.GroupHierarchy;
import io.taskmigo.identity.provisioning.IdentityProvisioningResult;
import io.taskmigo.identity.provisioning.application.port.in.api.GroupProvisioningService;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Reconciles managed Group profile and grants through canonical Identity and Access Control ports.
public final class DefaultGroupProvisioningService implements GroupProvisioningService {

    private final GroupCommandService groups;
    private final GroupHierarchyRepository hierarchies;
    private final SubjectGrantAssignmentService grantAssignments;
    private final SubjectGrantQueryService grantQueries;
    private final TransactionRunner transactions;

    public DefaultGroupProvisioningService(
        GroupCommandService groups,
        GroupHierarchyRepository hierarchies,
        SubjectGrantAssignmentService grantAssignments,
        SubjectGrantQueryService grantQueries,
        TransactionRunner transactions
    ) {
        this.groups = groups;
        this.hierarchies = hierarchies;
        this.grantAssignments = grantAssignments;
        this.grantQueries = grantQueries;
        this.transactions = transactions;
    }

    @Override
    public IdentityProvisioningResult<UUID> reconcileGroup(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> roleIds
    ) {
        return this.transactions.write(() -> this.reconcileGroupInTransaction(code, displayName, description, roleIds));
    }

    @Override
    public boolean deleteGroup(String code) {
        return this.transactions.write(() -> this.deleteGroupInTransaction(code));
    }

    private IdentityProvisioningResult<UUID> reconcileGroupInTransaction(
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
            throw invalidInput(exception);
        }

        UUID id = mutation.id();
        if (mutation.created()) {
            GroupHierarchy hierarchy = this.hierarchies.loadForMutation();
            this.hierarchies.synchronize(hierarchy);
            this.grantAssignments.setRoles(IdentitySubjects.group(id), requestedRoleIds);
            return new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.CREATED);
        }

        boolean rolesChanged = !this.grantQueries.roleIds(IdentitySubjects.group(id)).equals(requestedRoleIds);
        if (!mutation.changed() && !rolesChanged) {
            return new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.UNCHANGED);
        }
        if (rolesChanged) {
            this.grantAssignments.setRoles(IdentitySubjects.group(id), requestedRoleIds);
        }
        return new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.UPDATED);
    }

    private boolean deleteGroupInTransaction(String code) {
        Group existing;
        try {
            existing = this.groups.findByCode(code).orElse(null);
        } catch (GroupRuleViolation exception) {
            throw invalidInput(exception);
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

    private static GroupException invalidInput(GroupRuleViolation exception) {
        return new GroupException(GroupException.Type.INVALID_INPUT, exception.detail());
    }
}
