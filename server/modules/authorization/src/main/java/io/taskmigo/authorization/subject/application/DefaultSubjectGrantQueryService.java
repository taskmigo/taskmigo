package io.taskmigo.authorization.subject.application;

import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.api.SubjectGrantQueryService;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/// Reads direct Subject grants through the grant repository boundary.
@Transactional(readOnly = true)
public class DefaultSubjectGrantQueryService implements SubjectGrantQueryService {

    private final SubjectGrantRepository grants;

    public DefaultSubjectGrantQueryService(SubjectGrantRepository grants) {
        this.grants = grants;
    }

    @Override
    public Set<UUID> roleIds(SubjectRef subject) {
        return this.grants.load(subject).roleIds();
    }

    @Override
    public Set<UUID> statementIds(SubjectRef subject) {
        return this.grants.load(subject).statementIds();
    }
}
