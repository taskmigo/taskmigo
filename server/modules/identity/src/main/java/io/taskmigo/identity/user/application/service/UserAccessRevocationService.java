package io.taskmigo.identity.user.application.service;

import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.membership.application.port.in.internal.MembershipCleanupService;
import io.taskmigo.identity.user.application.port.out.UserSessionStore;
import io.taskmigo.identity.user.domain.User;
import java.util.Set;

/// Revokes all active access owned directly by a User without treating deletion cleanup as a normal User mutation.
public final class UserAccessRevocationService {

    private final SubjectGrantQueryService grantQueries;
    private final SubjectGrantAssignmentService grantAssignments;
    private final MembershipCleanupService memberships;
    private final UserSessionStore sessions;

    public UserAccessRevocationService(
        SubjectGrantQueryService grantQueries,
        SubjectGrantAssignmentService grantAssignments,
        MembershipCleanupService memberships,
        UserSessionStore sessions
    ) {
        this.grantQueries = grantQueries;
        this.grantAssignments = grantAssignments;
        this.memberships = memberships;
        this.sessions = sessions;
    }

    public UserAccessRevocation revoke(User user) {
        SubjectRef subject = IdentitySubjects.user(user.id());
        boolean roles = !this.grantQueries.roleIds(subject).isEmpty();
        boolean statements = !this.grantQueries.statementIds(subject).isEmpty();
        boolean sessions = this.sessions.revoke(user.username().value());

        this.grantAssignments.setRoles(subject, Set.of());
        this.grantAssignments.setStatements(subject, Set.of());
        boolean groups = this.memberships.removeAllForUser(user.id());

        return new UserAccessRevocation(roles, statements, groups, sessions);
    }
}
