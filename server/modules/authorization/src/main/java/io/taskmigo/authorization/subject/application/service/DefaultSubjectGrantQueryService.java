package io.taskmigo.authorization.subject.application.service;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.authorization.subject.application.port.out.SubjectGrantRepository;
import java.util.Set;
import java.util.UUID;

/// Reads direct Subject grants through the grant repository boundary.
public final class DefaultSubjectGrantQueryService implements SubjectGrantQueryService {

    private final SubjectGrantRepository grants;
    private final TransactionRunner transactions;

    public DefaultSubjectGrantQueryService(SubjectGrantRepository grants, TransactionRunner transactions) {
        this.grants = grants;
        this.transactions = transactions;
    }

    @Override
    public Set<UUID> roleIds(SubjectRef subject) {
        return this.transactions.read(() -> this.grants.load(subject).roleIds());
    }

    @Override
    public Set<UUID> statementIds(SubjectRef subject) {
        return this.transactions.read(() -> this.grants.load(subject).statementIds());
    }
}
