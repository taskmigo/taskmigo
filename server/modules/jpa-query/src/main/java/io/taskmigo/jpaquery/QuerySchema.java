package io.taskmigo.jpaquery;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchemaView;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Defines one operation-scoped, JPA-backed query surface for an entity root.
public abstract class QuerySchema<E> implements QuerySchemaView {

    private final Class<E> rootType;

    /// Creates a schema for one explicit persistence entity root.
    protected QuerySchema(Class<E> rootType) {
        this.rootType = Objects.requireNonNull(rootType);
    }

    /// Returns the operation identifier owned by this concrete schema.
    protected abstract String operationId();

    /// Returns the statically allow-listed JPA fields for this operation.
    protected abstract Collection<QueryField<E, ?>> staticFields();

    /// Returns runtime-discovered fields for this operation and context.
    protected abstract Collection<QueryField<E, ?>> runtimeFields(QueryFieldContext context);

    /// Returns the entity root type owned by this operation schema.
    public final Class<E> rootType() {
        return this.rootType;
    }

    @Override
    public final String operation() {
        String operation = Objects.requireNonNull(this.operationId());
        if (operation.isBlank()) {
            throw new IllegalStateException("QuerySchema operation id must not be blank");
        }
        return operation;
    }

    @Override
    public final Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
        return this.bindings(context).values().stream().map(QueryField::descriptor).toList();
    }

    /// Resolves the JPA-bound field for a logical path in the effective schema.
    public final Optional<QueryField<E, ?>> binding(QueryPath path, QueryFieldContext context) {
        Objects.requireNonNull(path);
        return Optional.ofNullable(this.bindings(context).get(path));
    }

    /// Resolves the JPA-bound field for a static-only context.
    public final Optional<QueryField<E, ?>> binding(QueryPath path) {
        return this.binding(path, QueryFieldContext.empty());
    }

    /// Creates one field from a generated singular JPA metamodel attribute.
    protected final <V> QueryField<E, V> field(SingularAttribute<? super E, V> attribute) {
        return QueryField.of(JpaPath.of(attribute));
    }

    /// Creates one field from a typed nested singular JPA path.
    protected final <V> QueryField<E, V> field(JpaPath<E, V> path) {
        return QueryField.of(path);
    }

    private Map<QueryPath, QueryField<E, ?>> bindings(QueryFieldContext context) {
        Objects.requireNonNull(context);
        LinkedHashMap<QueryPath, QueryField<E, ?>> effective = new LinkedHashMap<>();
        this.append(effective, this.staticFields());
        this.append(effective, this.runtimeFields(context));
        return Map.copyOf(effective);
    }

    private void append(Map<QueryPath, QueryField<E, ?>> target, Collection<QueryField<E, ?>> fields) {
        for (QueryField<E, ?> field : List.copyOf(Objects.requireNonNull(fields))) {
            QueryPath path = field.descriptor().path();
            if (target.putIfAbsent(path, field) != null) {
                throw new IllegalStateException(
                    "Duplicate query field path '" + path.text() + "' in operation " + this.operation()
                );
            }
        }
    }
}
