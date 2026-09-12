package io.taskmigo.identity.persistence.query;

import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.persistence.QueryPredicateModel;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/// Creates an Identity-owned Query Predicate binder for a flat or nested entity mapping.
public final class JpaQueryPredicateBinder<Q, E> implements QueryPredicateBinder<Q, E> {

    private final Class<Q> queryType;
    private final Class<E> domainType;
    private final Map<String, String> paths;
    private final Map<String, Class<?>> types;

    /// Creates a binder with explicit logical-to-physical paths and physical value types.
    public JpaQueryPredicateBinder(
        Class<Q> queryType,
        Class<E> domainType,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        this.queryType = queryType;
        this.domainType = domainType;
        this.paths = Map.copyOf(paths);
        this.types = Map.copyOf(types);
    }

    @Override
    public Class<Q> queryType() {
        return this.queryType;
    }

    @Override
    public Class<E> domainType() {
        return this.domainType;
    }

    @Override
    public Specification<E> bind(QueryPredicate<Q> predicate) {
        if (!(predicate instanceof QueryPredicateModel model)) {
            throw new IllegalArgumentException("unsupported Query Predicate implementation");
        }
        return JpaQueryExpressionBinder.bind(model.expression(), this.paths, this.types);
    }
}
