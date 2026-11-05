package io.taskmigo.authorization.statement.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.querybinding.jpa.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.querybinding.jpa.JpaQueryPredicateBinder;
import io.taskmigo.authorization.querybinding.jpa.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.querybinding.jpa.QueryPredicateBinder;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.application.StatementQueryRepository;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.jpaquery.JpaQuerySortSpecifications;
import io.taskmigo.jpaquery.JpaQuerySpecifications;
import io.taskmigo.query.ListQuerySpec;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/// Reads Statement projections and binds independent client-query and authorization contracts.
@Repository
public class JpaStatementQueryRepository implements StatementQueryRepository {

    private final StatementRepository statements;
    private final ListStatementsQuerySchema listSchema;
    private final QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder;

    JpaStatementQueryRepository(
        StatementRepository statements,
        ListStatementsQuerySchema querySchema,
        ListStatementsAuthorizationSchema authorizationSchema
    ) {
        this.statements = statements;
        this.listSchema = querySchema;
        this.queryBinder = new JpaQueryPredicateBinder<>(StatementInfo.class, querySchema);
        this.objectBinder = new JpaObjectAuthorizationPredicateBinder<>(StatementInfo.class, authorizationSchema);
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
        ListQuerySpec<StatementInfo> query,
        ObjectAuthorizationPredicate<StatementInfo> authorization
    ) {
        Sort pageableSort = JpaQuerySortSpecifications.hasSorting(query) ? Sort.unsorted() : Sort.by("id");
        var pageable = PageRequest.of(page - 1, perPage, pageableSort);
        Specification<StatementEntity> authorizationSpec = this.objectBinder.bind(authorization);
        Specification<StatementEntity> clientFilter = this.queryBinder.bind(query.predicate());
        Specification<StatementEntity> ordering = JpaQuerySortSpecifications.bind(query, this.listSchema);
        var result = this.statements.findAll(
            JpaQuerySpecifications.authorized(authorizationSpec, clientFilter).and(ordering),
            pageable
        );
        return new OffsetPage<>(
            result.map(StatementEntity::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }
}
