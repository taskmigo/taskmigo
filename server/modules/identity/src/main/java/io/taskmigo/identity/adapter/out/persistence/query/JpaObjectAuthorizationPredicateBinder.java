package io.taskmigo.identity.adapter.out.persistence.query;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModel;
import io.taskmigo.jpaquery.QuerySchema;
import org.springframework.data.jpa.domain.Specification;

/// Binds Object Authorization predicates through the same operation schema as client query filtering.
public final class JpaObjectAuthorizationPredicateBinder<Q, E> implements ObjectAuthorizationPredicateBinder<Q, E> {

    private final Class<Q> objectType;
    private final QuerySchema<E> schema;

    /// Creates a binder backed by one concrete operation schema.
    public JpaObjectAuthorizationPredicateBinder(Class<Q> objectType, QuerySchema<E> schema) {
        this.objectType = objectType;
        this.schema = schema;
    }

    @Override
    public Class<Q> objectType() {
        return this.objectType;
    }

    @Override
    public Class<E> domainType() {
        return this.schema.rootType();
    }

    @Override
    public Specification<E> bind(ObjectAuthorizationPredicate<Q> predicate) {
        if (!(predicate instanceof ObjectAuthorizationPredicateModel model)) {
            throw new IllegalArgumentException("unsupported Object Authorization Predicate implementation");
        }
        if (!model.schemaIdentity().equals(this.schema.identity())) {
            throw new IllegalArgumentException(
                "Object Authorization Predicate schema identity does not match the JPA operation schema"
            );
        }
        return JpaObjectAuthorizationExpressionBinder.bind(model.expression(), this.schema);
    }
}
