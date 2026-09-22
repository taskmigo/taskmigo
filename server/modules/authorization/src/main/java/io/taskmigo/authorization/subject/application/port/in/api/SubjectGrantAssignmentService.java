package io.taskmigo.authorization.subject.application.port.in.api;

import io.taskmigo.authorization.subject.SubjectRef;
import java.util.Collection;
import java.util.UUID;

/// Replaces direct Access Control grants for an opaque subject.
public interface SubjectGrantAssignmentService {
    /// Replaces the subject's direct Role grants after validating every referenced Role.
    void setRoles(SubjectRef subject, Collection<UUID> roleIds);

    /// Replaces the subject's direct Statement grants after validating every referenced Statement.
    void setStatements(SubjectRef subject, Collection<UUID> statementIds);
}
