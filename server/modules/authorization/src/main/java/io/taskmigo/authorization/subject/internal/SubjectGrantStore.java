package io.taskmigo.authorization.subject.internal;

import io.taskmigo.authorization.subject.SubjectRef;
import java.util.Set;
import java.util.UUID;

/// Defines persistence capabilities required by subject-grant application use cases.
public interface SubjectGrantStore {
    void replaceRoleIds(SubjectRef subject, Set<UUID> roleIds);

    void replaceStatementIds(SubjectRef subject, Set<UUID> statementIds);

    Set<UUID> roleIds(SubjectRef subject);

    Set<UUID> statementIds(SubjectRef subject);
}
