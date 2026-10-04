package io.taskmigo.identity.adapter.out.persistence.query;

import io.taskmigo.language.FieldId;
import io.taskmigo.query.QueryBinding;
import io.taskmigo.query.QueryFieldBinding;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.model.QueryPredicateModel;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;

/// Creates a resource-owned Query Predicate binder from the selected execution binding.
public final class JpaQueryPredicateBinder<Q, E> implements QueryPredicateBinder<Q, E> {

    private final Class<Q> queryType;
    private final Class<E> domainType;
    private final QueryBinding<Q> binding;
    private final Map<FieldId, String> paths;
    private final Map<FieldId, Class<?>> types;

    /// Creates a binder whose physical paths come only from the selected Query execution binding.
    public JpaQueryPredicateBinder(Class<E> domainType, QueryBinding<Q> binding, Map<FieldId, Class<?>> types) {
        this.domainType = Objects.requireNonNull(domainType);
        this.binding = Objects.requireNonNull(binding);
        this.queryType = binding.queryType();
        this.types = Map.copyOf(types);
        HashMap<FieldId, String> declaredPaths = new HashMap<>();
        for (QueryFieldBinding field : binding.fields()) {
            if (!this.types.containsKey(field.id())) {
                throw new IllegalArgumentException("Persistence type is not bound: " + field.id().value());
            }
            if (declaredPaths.putIfAbsent(field.id(), field.executionPath().text()) != null) {
                throw new IllegalArgumentException("duplicate persistence field binding: " + field.id().value());
            }
        }
        this.paths = Map.copyOf(declaredPaths);
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
        if (!this.binding.identity().equals(model.bindingIdentity())) {
            throw new IllegalArgumentException("Query Predicate belongs to an incompatible execution binding");
        }
        return JpaQueryExpressionBinder.bind(model.expression(), this.paths, this.types);
    }
}
