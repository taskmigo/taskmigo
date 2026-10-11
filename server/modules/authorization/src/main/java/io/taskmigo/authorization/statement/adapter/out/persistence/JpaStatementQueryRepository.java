package io.taskmigo.authorization.statement.adapter.out.persistence;

import io.taskmigo.authorization.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.application.port.out.StatementQueryRepository;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.jpaquery.JpaQuerySpecifications;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/// Reads Statement projections and binds client and authorization predicates through the list operation schema.
@Repository
public class JpaStatementQueryRepository implements StatementQueryRepository {

    private final StatementRepository statements;
    private final QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder;

    JpaStatementQueryRepository(StatementRepository statements, ListStatementsQuerySchema schema) {
        this.statements = statements;
        this.queryBinder = new JpaQueryPredicateBinder<>(StatementInfo.class, schema);
        this.objectBinder = new JpaObjectAuthorizationPredicateBinder<>(StatementInfo.class, schema);
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
        Specification<StatementEntity> authorizationSpec = this.objectBinder.bind(authorization);
        Specification<StatementEntity> clientFilter = this.queryBinder.bind(filter);
        var result = this.statements.findAll(
            JpaQuerySpecifications.authorized(authorizationSpec, clientFilter),
            pageable
        );
        return new OffsetPage<>(
            result.map(StatementEntity::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }
}
