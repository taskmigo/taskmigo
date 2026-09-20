package io.taskmigo.authorization.subject.domain;

import io.taskmigo.authorization.subject.SubjectRef;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/// Models the direct Role and Statement grants owned by one authorization subject.
public record SubjectGrants(SubjectRef subject, Set<UUID> roleIds, Set<UUID> statementIds) {
    /// Restores canonical direct-grant state.
    public SubjectGrants {
        Objects.requireNonNull(subject, "subject");
        roleIds = Set.copyOf(roleIds);
        statementIds = Set.copyOf(statementIds);
    }

    /// Returns state with the direct Role grants replaced as one set-like assignment.
    public SubjectGrants replacingRoles(Collection<UUID> requestedRoleIds) {
        return new SubjectGrants(this.subject, Set.copyOf(requestedRoleIds), this.statementIds);
    }

    /// Returns state with the direct Statement grants replaced as one set-like assignment.
    public SubjectGrants replacingStatements(Collection<UUID> requestedStatementIds) {
        return new SubjectGrants(this.subject, this.roleIds, Set.copyOf(requestedStatementIds));
    }
}
