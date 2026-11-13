package io.taskmigo.identity.provisioning.application;

import io.taskmigo.audit.application.record.AuditRecorder;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.audit.model.AuditChanges;
import io.taskmigo.audit.model.AuditOperation;
import io.taskmigo.authorization.subject.application.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.api.SubjectGrantQueryService;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.GroupException;
import io.taskmigo.identity.group.application.GroupHierarchyRepository;
import io.taskmigo.identity.group.application.internal.GroupCommandService;
import io.taskmigo.identity.group.application.internal.GroupMutationResult;
import io.taskmigo.identity.group.domain.Group;
import io.taskmigo.identity.group.domain.GroupRuleViolation;
import io.taskmigo.identity.group.domain.hierarchy.GroupHierarchy;
import io.taskmigo.identity.provisioning.IdentityProvisioningResult;
import io.taskmigo.identity.provisioning.application.api.GroupProvisioningService;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

/// Reconciles managed Group profile and grants through canonical Identity and Access Control contracts.
@Transactional
public class DefaultGroupProvisioningService implements GroupProvisioningService {

    private final GroupCommandService groups;
    private final GroupHierarchyRepository hierarchies;
    private final SubjectGrantAssignmentService grantAssignments;
    private final SubjectGrantQueryService grantQueries;
    private final AuditRecorder audits;

    public DefaultGroupProvisioningService(
        GroupCommandService groups,
        GroupHierarchyRepository hierarchies,
        SubjectGrantAssignmentService grantAssignments,
        SubjectGrantQueryService grantQueries,
        AuditRecorder audits
    ) {
        this.groups = groups;
        this.hierarchies = hierarchies;
        this.grantAssignments = grantAssignments;
        this.grantQueries = grantQueries;
        this.audits = audits;
    }

    @Override
    public IdentityProvisioningResult<UUID> reconcileGroup(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> roleIds
    ) {
        return this.reconcileGroupInTransaction(code, displayName, description, roleIds);
    }

    @Override
    public boolean deleteGroup(String code) {
        return this.deleteGroupInTransaction(code);
    }

    private IdentityProvisioningResult<UUID> reconcileGroupInTransaction(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> roleIds
    ) {
        Set<UUID> requestedRoleIds = Set.copyOf(roleIds);
        Group before = this.groups.findByCode(code).orElse(null);
        String previousName = before == null ? null : before.profile().displayName();
        String previousDescription = before == null ? null : before.profile().description();
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
            this.audits.record("group", id, AuditOperation.CREATE, () -> {
                Group created = this.groups.findByCode(code).orElseThrow();
                return AuditChanges.builder()
                    .add(AuditChange.visible("code", null, created.code().value()))
                    .changed("displayName", null, created.profile().displayName())
                    .changed("description", null, created.profile().description())
                    .changed("roleIds", null, ordered(requestedRoleIds))
                    .build();
            });
            return new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.CREATED);
        }

        Set<UUID> previousRoles = Set.copyOf(this.grantQueries.roleIds(IdentitySubjects.group(id)));
        boolean rolesChanged = !previousRoles.equals(requestedRoleIds);
        if (!mutation.changed() && !rolesChanged) {
            return new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.UNCHANGED);
        }
        if (rolesChanged) {
            this.grantAssignments.setRoles(IdentitySubjects.group(id), requestedRoleIds);
        }
        this.audits.record("group", id, AuditOperation.UPDATE, () -> {
            Group after = this.groups.findByCode(code).orElseThrow();
            return AuditChanges.builder()
                .changed("displayName", previousName, after.profile().displayName())
                .changed("description", previousDescription, after.profile().description())
                .changed("roleIds", ordered(previousRoles), ordered(requestedRoleIds))
                .build();
        });
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
        this.audits.record("group", existing.id(), AuditOperation.DELETE, () ->
            List.of(AuditChange.visible("code", existing.code().value(), null))
        );
        return true;
    }

    private static List<String> ordered(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).sorted().toList();
    }

    private static GroupException invalidInput(GroupRuleViolation exception) {
        return new GroupException(GroupException.Type.INVALID_INPUT, exception.detail());
    }
}
