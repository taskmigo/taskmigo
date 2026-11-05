package io.taskmigo.identity.persistence.query;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModel;
import io.taskmigo.jpaquery.QuerySchema;
import org.springframework.data.jpa.domain.Specification;

/// Binds Object Authorization predicates through the persistence mapping owned by an Authorization schema.
public final class JpaObjectAuthorizationPredicateBinder<Q, E> implements ObjectAuthorizationPredicateBinder<Q, E> {

    private final Class<Q> objectType;
    private final QuerySchema<E> bindingSchema;
    private final ObjectAuthorizationSchema<Q> authorizationSchema;

    /// Creates a binder backed by one concrete JPA authorization schema.
    public <S extends QuerySchema<E> & ObjectAuthorizationSchema<Q>> JpaObjectAuthorizationPredicateBinder(
        Class<Q> objectType,
        S schema
    ) {
        this.objectType = objectType;
        this.bindingSchema = schema;
        this.authorizationSchema = schema;
    }

    @Override
    public Class<Q> objectType() {
        return this.objectType;
    }

    @Override
    public Class<E> domainType() {
        return this.bindingSchema.rootType();
    }

    @Override
    public Specification<E> bind(ObjectAuthorizationPredicate<Q> predicate) {
        if (!(predicate instanceof ObjectAuthorizationPredicateModel model)) {
            throw new IllegalArgumentException("unsupported Object Authorization Predicate implementation");
        }
        if (!model.schemaIdentity().equals(this.authorizationSchema.authorizationIdentity())) {
            throw new IllegalArgumentException(
                "Object Authorization Predicate schema identity does not match the JPA authorization schema"
            );
        }
        return JpaObjectAuthorizationExpressionBinder.bind(model.expression(), this.bindingSchema);
    }
}
