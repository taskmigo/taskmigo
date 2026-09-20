package io.taskmigo.authorization.subject.application;

import io.taskmigo.authorization.subject.SubjectGrantQueryService;
import io.taskmigo.authorization.subject.SubjectRef;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Reads direct Subject grants through the grant repository boundary.
@Service
class DefaultSubjectGrantQueryService implements SubjectGrantQueryService {

    private final SubjectGrantRepository grants;

    DefaultSubjectGrantQueryService(SubjectGrantRepository grants) {
        this.grants = grants;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> roleIds(SubjectRef subject) {
        return this.grants.load(subject).roleIds();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> statementIds(SubjectRef subject) {
        return this.grants.load(subject).statementIds();
    }
}
