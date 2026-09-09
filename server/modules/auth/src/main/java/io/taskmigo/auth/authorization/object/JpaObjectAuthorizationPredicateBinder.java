package io.taskmigo.auth.authorization.object;

import io.taskmigo.auth.resourcequery.JpaSemanticPredicateBinder;
import io.taskmigo.auth.resourcequery.ObjectAuthorizationPredicateBinder;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/// Creates a resource-owned Object Authorization predicate binder for an entity mapping.
public final class JpaObjectAuthorizationPredicateBinder<Q, E> implements ObjectAuthorizationPredicateBinder<Q, E> {

    private final Class<Q> objectType;
    private final Class<E> domainType;
    private final Map<String, String> paths;
    private final Map<String, Class<?>> types;

    /// Creates a binder with explicit logical-to-physical paths and physical value types.
    public JpaObjectAuthorizationPredicateBinder(
        Class<Q> objectType,
        Class<E> domainType,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        this.objectType = objectType;
        this.domainType = domainType;
        this.paths = Map.copyOf(paths);
        this.types = Map.copyOf(types);
    }

    @Override
    public Class<Q> objectType() {
        return this.objectType;
    }

    @Override
    public Class<E> domainType() {
        return this.domainType;
    }

    @Override
    public Specification<E> bind(ObjectAuthorizationPredicate<Q> predicate) {
        return JpaSemanticPredicateBinder.bind(
            ObjectAuthorizationPredicateFactory.expression(predicate),
            this.paths,
            this.types
        );
    }
}
