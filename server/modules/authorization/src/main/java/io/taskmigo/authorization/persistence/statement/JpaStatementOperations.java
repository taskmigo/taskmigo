package io.taskmigo.authorization.persistence.statement;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.statement.StatementDefinition;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.internal.StatementStore;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/// Implements Statement persistence operations with JPA entities and repositories.
@Service
public class JpaStatementOperations implements StatementStore {

    private final StatementRepository statements;
    private final QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder;

    public JpaStatementOperations(
        StatementRepository statements,
        QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder
    ) {
        this.statements = statements;
        this.queryBinder = queryBinder;
        this.objectBinder = objectBinder;
    }

    @Override
    public boolean existsByCode(String code) {
        return this.statements.existsByCode(code);
    }

    @Override
    public UUID create(StatementDefinition definition) {
        UUID id = UUID.randomUUID();
        this.statements.save(new StatementEntity(id, definition));
        return id;
    }

    @Override
    public Optional<UUID> findIdByCode(String code) {
        return this.statements.findByCode(code).map(StatementEntity::id);
    }

    @Override
    public void update(UUID id, StatementDefinition definition) {
        StatementEntity statement = this.statements
            .findById(id)
            .orElseThrow(() -> new IllegalStateException("Statement does not exist: " + id));
        statement.update(definition);
        this.statements.flush();
    }

    @Override
    public void delete(UUID id) {
        this.statements.deleteById(id);
        this.statements.flush();
    }

    @Override
    public OffsetPage<StatementInfo> list(int page, int perPage) {
        var result = this.statements.findAllBy(PageRequest.of(page - 1, perPage, Sort.by("id")));
        return new OffsetPage<>(
            result.map(StatementEntity::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    @Override
    public OffsetPage<StatementInfo> list(
        int page,
        int perPage,
        QueryPredicate<StatementInfo> filter,
        ObjectAuthorizationPredicate<StatementInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var result = this.statements.findAll(
            this.queryBinder.bind(filter).and(this.objectBinder.bind(authorization)),
            pageable
        );
        return new OffsetPage<>(
            result.map(StatementEntity::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    @Override
    public boolean containsAll(Collection<UUID> ids) {
        Set<UUID> requestedIds = Set.copyOf(ids);
        return this.statements.findAllById(requestedIds).size() == requestedIds.size();
    }
}
