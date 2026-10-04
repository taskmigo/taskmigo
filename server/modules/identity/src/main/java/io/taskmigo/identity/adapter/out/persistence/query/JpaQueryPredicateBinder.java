package io.taskmigo.identity.adapter.out.persistence.query;

import io.taskmigo.language.FieldId;
import io.taskmigo.query.QueryBinding;
import io.taskmigo.query.QueryBindingResolver;
import io.taskmigo.query.QueryFieldBinding;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.model.QueryPredicateModel;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/// Creates a resource-owned Query Predicate binder from the exact execution binding retained by each predicate.
public final class JpaQueryPredicateBinder<Q, E> implements QueryPredicateBinder<Q, E> {

    private final Class<Q> queryType;
    private final Class<E> domainType;
    private final QueryBinding<Q> defaultBinding;
    private final @Nullable QueryBindingResolver bindings;

    /// Creates a static-only binder for applications that do not expose runtime schemas.
    public JpaQueryPredicateBinder(Class<E> domainType, QueryBinding<Q> binding) {
        this(domainType, binding, null);
    }

    /// Creates a binder that falls back to the startup binding and resolves runtime predicate identities when needed.
    public JpaQueryPredicateBinder(
        Class<E> domainType,
        QueryBinding<Q> binding,
        @Nullable QueryBindingResolver bindings
    ) {
        this.domainType = Objects.requireNonNull(domainType);
        this.defaultBinding = Objects.requireNonNull(binding);
        this.queryType = binding.queryType();
        this.bindings = bindings;
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
        Execution execution = execution(this.binding(model.bindingIdentity()));
        return JpaQueryExpressionBinder.bind(model.expression(), execution.paths(), execution.types());
    }

    private QueryBinding<?> binding(String bindingIdentity) {
        if (bindingIdentity.isEmpty() || this.defaultBinding.identity().equals(bindingIdentity)) {
            return this.defaultBinding;
        }
        if (this.bindings == null) {
            throw new IllegalArgumentException("Query Predicate belongs to an incompatible execution binding");
        }
        QueryBinding<?> resolved = this.bindings.resolve(this.queryType, bindingIdentity);
        if (!resolved.queryType().equals(this.queryType) || !resolved.identity().equals(bindingIdentity)) {
            throw new IllegalArgumentException("Query binding resolver returned an incompatible execution binding");
        }
        return resolved;
    }

    private static Execution execution(QueryBinding<?> binding) {
        HashMap<FieldId, String> paths = new HashMap<>();
        HashMap<FieldId, Class<?>> types = new HashMap<>();
        for (QueryFieldBinding field : binding.fields()) {
            if (paths.putIfAbsent(field.id(), field.executionPath().text()) != null) {
                throw new IllegalArgumentException("duplicate persistence field binding: " + field.id().value());
            }
            types.put(field.id(), field.valueType());
        }
        return new Execution(Map.copyOf(paths), Map.copyOf(types));
    }

    private record Execution(Map<FieldId, String> paths, Map<FieldId, Class<?>> types) {}
}
