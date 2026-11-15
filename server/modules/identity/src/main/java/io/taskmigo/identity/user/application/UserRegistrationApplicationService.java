package io.taskmigo.identity.user.application;

import io.taskmigo.audit.application.record.AuditRecorder;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.audit.model.AuditChanges;
import io.taskmigo.audit.model.AuditOperation;
import io.taskmigo.authorization.subject.application.api.SubjectGrantAssignmentService;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.application.api.GroupService;
import io.taskmigo.identity.membership.application.api.MembershipService;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.application.api.UserRegistrationService;
import io.taskmigo.identity.user.application.internal.UserCommandService;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.UserRuleViolation;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates User creation with initial Access Control and Group assignments as one application transaction.
@Transactional
public class UserRegistrationApplicationService implements UserRegistrationService {

    private final UserCommandService users;
    private final SubjectGrantAssignmentService grantAssignments;
    private final GroupService groups;
    private final MembershipService memberships;
    private final AuditRecorder audits;

    public UserRegistrationApplicationService(
        UserCommandService users,
        SubjectGrantAssignmentService grantAssignments,
        GroupService groups,
        MembershipService memberships,
        AuditRecorder audits
    ) {
        this.users = users;
        this.grantAssignments = grantAssignments;
        this.groups = groups;
        this.memberships = memberships;
        this.audits = audits;
    }

    @Override
    public UUID register(
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable Collection<UUID> roleIds,
        @Nullable Collection<UUID> groupIds
    ) {
        Set<UUID> requestedRoleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        Set<UUID> requestedGroupIds = groupIds == null ? Set.of() : Set.copyOf(groupIds);
        this.groups.requireGroups(requestedGroupIds);

        UUID userId;
        try {
            userId = this.users.createRuntime(username, emails, firstName, lastName);
        } catch (UserRuleViolation exception) {
            throw new UserException(UserException.Type.INVALID_INPUT, exception.detail(), exception);
        }

        this.grantAssignments.setRoles(IdentitySubjects.user(userId), requestedRoleIds);
        this.memberships.setGroupsForUser(userId, requestedGroupIds);
        this.audits.record("user", userId, AuditOperation.CREATE, () -> {
            User created = this.users.findByIdForUpdate(userId).orElseThrow();
            return AuditChanges.builder()
                .add(AuditChange.visible("username", null, created.username().value()))
                .changed("emails", null, created.profile().emails().stream().sorted().toList())
                .changed("firstName", null, created.profile().firstName())
                .changed("lastName", null, created.profile().lastName())
                .changed("roleIds", null, ordered(requestedRoleIds))
                .changed("groupIds", null, ordered(requestedGroupIds))
                .build();
        });
        return userId;
    }

    private static List<String> ordered(Collection<UUID> ids) {
        return ids.stream().sorted().map(UUID::toString).toList();
    }
}
