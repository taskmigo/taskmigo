package io.taskmigo.identity.adapter.out.persistence.query;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModel;
import io.taskmigo.language.FieldId;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/// Creates a resource-owned Object Authorization predicate binder for an entity mapping.
public final class JpaObjectAuthorizationPredicateBinder<Q, E> implements ObjectAuthorizationPredicateBinder<Q, E> {

    private final Class<Q> objectType;
    private final Class<E> domainType;
    private final Map<FieldId, String> paths;
    private final Map<FieldId, Class<?>> types;

    /// Creates a binder with explicit logical-to-physical paths and physical value types.
    public JpaObjectAuthorizationPredicateBinder(
        Class<Q> objectType,
        Class<E> domainType,
        Map<FieldId, String> paths,
        Map<FieldId, Class<?>> types
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
        if (!(predicate instanceof ObjectAuthorizationPredicateModel model)) {
            throw new IllegalArgumentException("unsupported Object Authorization Predicate implementation");
        }
        return JpaObjectAuthorizationExpressionBinder.bind(model.expression(), this.paths, this.types);
    }
}
