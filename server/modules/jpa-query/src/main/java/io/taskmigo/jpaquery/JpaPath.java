package io.taskmigo.jpaquery;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.query.QueryPath;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Represents a type-safe singular JPA attribute path that can be exposed by an operation query schema.
public final class JpaPath<E, V> {

    private final List<SingularAttribute<?, ?>> attributes;

    private JpaPath(List<SingularAttribute<?, ?>> attributes) {
        this.attributes = List.copyOf(attributes);
    }

    /// Starts a path at one singular JPA metamodel attribute.
    public static <E, V> JpaPath<E, V> of(SingularAttribute<? super E, V> attribute) {
        return new JpaPath<>(List.of(Objects.requireNonNull(attribute)));
    }

    /// Rejects collection-valued paths because collection traversal is outside Enhancement #237.
    public static <E, C, V> void of(PluralAttribute<? super E, C, V> attribute) {
        Objects.requireNonNull(attribute);
        throw new IllegalArgumentException("collection-valued JPA paths are not supported: " + attribute.getName());
    }

    /// Appends one singular attribute while preserving the leaf type.
    public <N> JpaPath<E, N> then(SingularAttribute<? super V, N> attribute) {
        Objects.requireNonNull(attribute);
        List<SingularAttribute<?, ?>> nested = new ArrayList<>(this.attributes.size() + 1);
        nested.addAll(this.attributes);
        nested.add(attribute);
        return new JpaPath<>(nested);
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

    /// Resolves this metadata path from one Criteria root/path.
    public Path<?> resolve(Path<E> root) {
        Path<?> current = Objects.requireNonNull(root);
        for (SingularAttribute<?, ?> attribute : this.attributes) {
            current = current.get(attribute.getName());
        }
        return current;
    }

    /// Returns the Java type of the leaf attribute for persistence-value coercion.
    public Class<?> javaType() {
        return this.attributes.getLast().getJavaType();
    }
}
