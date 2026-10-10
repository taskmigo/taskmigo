package io.taskmigo.jpaquery;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.query.QueryPath;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// Represents a type-safe singular JPA attribute path that can be exposed by an operation query schema.
public final class JpaPath<E, V> {

    private final List<SingularAttribute<?, ?>> attributes;
    private final Function<Path<E>, Path<V>> resolver;

    private JpaPath(List<SingularAttribute<?, ?>> attributes, Function<Path<E>, Path<V>> resolver) {
        this.attributes = List.copyOf(attributes);
        this.resolver = Objects.requireNonNull(resolver);
    }

    /// Starts a path at one singular JPA metamodel attribute.
    public static <E, V> JpaPath<E, V> of(SingularAttribute<? super E, V> attribute) {
        Objects.requireNonNull(attribute);
        return new JpaPath<>(List.of(attribute), root -> Objects.requireNonNull(root.get(attribute)));
    }

    /// Rejects collection-valued paths because collection traversal is outside Enhancement #237.
    public static <E, C, V> void of(PluralAttribute<? super E, C, V> attribute) {
        Objects.requireNonNull(attribute);
        throw new IllegalArgumentException("collection-valued JPA paths are not supported: " + attribute.getName());
    }

    /// Appends one singular embedded/to-one attribute while preserving the leaf type.
    public <N> JpaPath<E, N> then(SingularAttribute<? super V, N> attribute) {
        Objects.requireNonNull(attribute);
        SingularAttribute<?, ?> current = this.attributes.getLast();
        requireTraversable(current);
        List<SingularAttribute<?, ?>> nested = new ArrayList<>(this.attributes.size() + 1);
        nested.addAll(this.attributes);
        nested.add(attribute);
        return new JpaPath<>(nested, root -> Objects.requireNonNull(this.resolve(root).get(attribute)));
    }

    /// Rejects collection-valued traversal from any nested segment.
    public <C, N> void then(PluralAttribute<? super V, C, N> attribute) {
        Objects.requireNonNull(attribute);
        throw new IllegalArgumentException("collection-valued JPA paths are not supported: " + attribute.getName());
    }

    /// Returns the logical path derived from JPA metamodel attribute names.
    public QueryPath queryPath() {
        return new QueryPath(this.attributes.stream().map(SingularAttribute::getName).toList());
    }

    /// Returns the Java type of the leaf attribute.
    public TypeDescriptor type() {
        return TypeDescriptor.of(this.attributes.getLast().getJavaType());
    }

    /// Returns whether any singular segment may be null.
    public boolean nullable() {
        return this.attributes.stream().anyMatch(SingularAttribute::isOptional);
    }

    /// Resolves this metadata path from one Criteria root using typed JPA metamodel attributes.
    public Path<V> resolve(Path<E> root) {
        return Objects.requireNonNull(this.resolver.apply(Objects.requireNonNull(root)));
    }

    /// Returns the Java type of the leaf attribute for persistence-value coercion.
    public Class<?> javaType() {
        return this.attributes.getLast().getJavaType();
    }

    private static void requireTraversable(SingularAttribute<?, ?> attribute) {
        PersistentAttributeType type = Objects.requireNonNull(attribute.getPersistentAttributeType());
        if (
            type != PersistentAttributeType.EMBEDDED &&
            type != PersistentAttributeType.MANY_TO_ONE &&
            type != PersistentAttributeType.ONE_TO_ONE
        ) {
            throw new IllegalArgumentException(
                "JPA path segment '" + attribute.getName() + "' is not embedded or to-one: " + type
            );
        }
    }
}
