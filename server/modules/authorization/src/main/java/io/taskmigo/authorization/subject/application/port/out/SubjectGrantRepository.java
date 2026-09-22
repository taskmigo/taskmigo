package io.taskmigo.authorization.subject.application.port.out;

import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.domain.SubjectGrants;

/// Persists direct Subject grants without exposing binding-table mechanics.
public interface SubjectGrantRepository {
    SubjectGrants load(SubjectRef subject);

    void saveRoles(SubjectGrants grants);

    void saveStatements(SubjectGrants grants);
}
