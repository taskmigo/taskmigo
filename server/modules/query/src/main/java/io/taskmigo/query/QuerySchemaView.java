package io.taskmigo.query;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

/// Exposes the persistence-neutral metadata of one operation-scoped query schema.
public interface QuerySchemaView {
    /// Returns the stable operation identifier that owns this schema.
    String operation();

    /// Resolves the effective static and runtime field allowlist for one operation context.
    Collection<QueryFieldDescriptor> fields(QueryFieldContext context);

    /// Resolves the effective field allowlist for a static-only context.
    default Collection<QueryFieldDescriptor> fields() {
        return this.fields(QueryFieldContext.empty());
    }

    /// Resolves one effective field by its logical path.
    default Optional<QueryFieldDescriptor> field(QueryPath path, QueryFieldContext context) {
        return this.fields(context)
            .stream()
            .filter(field -> field.path().equals(path))
            .findFirst();
    }

    /// Resolves one effective field for a static-only context.
    default Optional<QueryFieldDescriptor> field(QueryPath path) {
        return this.field(path, QueryFieldContext.empty());
    }

    /// Returns a deterministic identity for the effective operation schema.
    default String identity(QueryFieldContext context) {
        String fields = this.fields(context)
            .stream()
            .sorted(Comparator.comparing(field -> field.path().text()))
            .map(
                field ->
                    field.path().text() +
                    ":" +
                    field.type().identity() +
                    ":" +
                    field.nullable() +
                    ":" +
                    field.operators().stream().map(Enum::name).sorted().collect(Collectors.joining(","))
            )
            .collect(Collectors.joining("|"));
        return this.operation() + "[" + fields + "]";
    }

    /// Returns the identity for a static-only context.
    default String identity() {
        return this.identity(QueryFieldContext.empty());
    }
}
