package io.taskmigo.authorization.statement.adapter.out.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.application.port.out.StatementQueryRepository;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/// Reads Statement projections directly from JPA while keeping mutation aggregate loading separate.
@Repository
public class JpaStatementQueryRepository implements StatementQueryRepository {

    private final StatementRepository statements;
    private final QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder;

    JpaStatementQueryRepository(
        StatementRepository statements,
        QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder
    ) {
        this.statements = statements;
        this.queryBinder = queryBinder;
        this.objectBinder = objectBinder;
    }

    @Override
    public boolean containsAll(Collection<UUID> ids) {
        Set<UUID> requested = Set.copyOf(ids);
        return this.statements.findAllById(requested).size() == requested.size();
    }

    @Override
    public OffsetPage<StatementInfo> list(int page, int perPage) {
        var result = this.statements.findAll(PageRequest.of(page - 1, perPage, Sort.by("id")));
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
}
