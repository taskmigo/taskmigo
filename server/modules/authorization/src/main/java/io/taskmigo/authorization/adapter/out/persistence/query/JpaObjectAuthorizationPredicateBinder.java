package io.taskmigo.authorization.adapter.out.persistence.query;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationBindingResolver;
import io.taskmigo.authorization.object.ObjectAuthorizationFieldBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModel;
import io.taskmigo.language.FieldId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/// Creates a resource-owned Object Authorization binder from the exact execution binding retained by each predicate.
public final class JpaObjectAuthorizationPredicateBinder<Q, E> implements ObjectAuthorizationPredicateBinder<Q, E> {

    private final Class<Q> objectType;
    private final Class<E> domainType;
    private final ObjectAuthorizationBinding<Q> defaultBinding;
    private final @Nullable ObjectAuthorizationBindingResolver bindings;

    /// Creates a static-only binder for applications that do not expose runtime schemas.
    public JpaObjectAuthorizationPredicateBinder(Class<E> domainType, ObjectAuthorizationBinding<Q> binding) {
        this(domainType, binding, null);
    }

    /// Creates a binder that falls back to the startup binding and resolves runtime predicate identities when needed.
    public JpaObjectAuthorizationPredicateBinder(
        Class<E> domainType,
        ObjectAuthorizationBinding<Q> binding,
        @Nullable ObjectAuthorizationBindingResolver bindings
    ) {
        this.domainType = Objects.requireNonNull(domainType);
        this.defaultBinding = Objects.requireNonNull(binding);
        this.objectType = binding.objectType();
        this.bindings = bindings;
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
        Execution execution = execution(this.binding(model.bindingIdentity()));
        return JpaObjectAuthorizationExpressionBinder.bind(model.expression(), execution.paths(), execution.types());
    }

    private ObjectAuthorizationBinding<?> binding(String bindingIdentity) {
        if (bindingIdentity.isEmpty() || this.defaultBinding.identity().equals(bindingIdentity)) {
            return this.defaultBinding;
        }
        if (this.bindings == null) {
            throw new IllegalArgumentException(
                "Object Authorization Predicate belongs to an incompatible execution binding"
            );
        }
        ObjectAuthorizationBinding<?> resolved = this.bindings.resolve(this.objectType, bindingIdentity);
        if (!resolved.objectType().equals(this.objectType) || !resolved.identity().equals(bindingIdentity)) {
            throw new IllegalArgumentException(
                "Object Authorization binding resolver returned an incompatible execution binding"
            );
        }
        return resolved;
    }

    private static Execution execution(ObjectAuthorizationBinding<?> binding) {
        HashMap<FieldId, String> paths = new HashMap<>();
        HashMap<FieldId, Class<?>> types = new HashMap<>();
        for (ObjectAuthorizationFieldBinding field : binding.fields()) {
            if (paths.putIfAbsent(field.id(), field.executionPath()) != null) {
                throw new IllegalArgumentException("duplicate persistence field binding: " + field.id().value());
            }
            types.put(field.id(), field.valueType());
        }
        return new Execution(Map.copyOf(paths), Map.copyOf(types));
    }

    private record Execution(Map<FieldId, String> paths, Map<FieldId, Class<?>> types) {}
}
