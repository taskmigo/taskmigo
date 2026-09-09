package io.taskmigo.auth.authorization.object;

import io.taskmigo.auth.identityquery.JpaSemanticPredicateBinder;
import io.taskmigo.auth.identityquery.ObjectAuthorizationPredicateBinder;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.domain.Specification;

/// Creates an Identity-owned Object Authorization predicate binder for an entity mapping.
@NullMarked
public final class IdentityJpaObjectAuthorizationPredicateBinder<Q, E>
    implements ObjectAuthorizationPredicateBinder<Q, E> {

    private final Class<Q> objectType;
    private final Class<E> domainType;
    private final Map<String, String> paths;
    private final Map<String, Class<?>> types;

    /// Creates a binder with explicit logical-to-physical paths and physical value types.
    public IdentityJpaObjectAuthorizationPredicateBinder(
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
        return JpaSemanticPredicateBinder.bind(ObjectAuthorizationPredicateFactory.expression(predicate), this.paths, this.types);
    }
}
