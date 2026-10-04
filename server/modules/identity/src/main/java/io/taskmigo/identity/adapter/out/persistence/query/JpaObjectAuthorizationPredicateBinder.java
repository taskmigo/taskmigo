package io.taskmigo.identity.adapter.out.persistence.query;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationFieldBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModel;
import io.taskmigo.language.FieldId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;

/// Creates a resource-owned Object Authorization predicate binder from the selected execution binding.
public final class JpaObjectAuthorizationPredicateBinder<Q, E> implements ObjectAuthorizationPredicateBinder<Q, E> {

    private final Class<Q> objectType;
    private final Class<E> domainType;
    private final ObjectAuthorizationBinding<Q> binding;
    private final Map<FieldId, String> paths;
    private final Map<FieldId, Class<?>> types;

    /// Creates a binder whose physical paths come only from the selected Object Authorization execution binding.
    public JpaObjectAuthorizationPredicateBinder(
        Class<E> domainType,
        ObjectAuthorizationBinding<Q> binding,
        Map<FieldId, Class<?>> types
    ) {
        this.domainType = Objects.requireNonNull(domainType);
        this.binding = Objects.requireNonNull(binding);
        this.objectType = binding.objectType();
        this.types = Map.copyOf(types);
        HashMap<FieldId, String> declaredPaths = new HashMap<>();
        for (ObjectAuthorizationFieldBinding field : binding.fields()) {
            if (!this.types.containsKey(field.id())) {
                throw new IllegalArgumentException("Persistence type is not bound: " + field.id().value());
            }
            if (declaredPaths.putIfAbsent(field.id(), field.executionPath()) != null) {
                throw new IllegalArgumentException("duplicate persistence field binding: " + field.id().value());
            }
        }
        this.paths = Map.copyOf(declaredPaths);
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
        if (!this.binding.identity().equals(model.bindingIdentity())) {
            throw new IllegalArgumentException(
                "Object Authorization Predicate belongs to an incompatible execution binding"
            );
        }
        return JpaObjectAuthorizationExpressionBinder.bind(model.expression(), this.paths, this.types);
    }
}
