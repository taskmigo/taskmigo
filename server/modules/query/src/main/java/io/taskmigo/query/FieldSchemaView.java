package io.taskmigo.query;

import java.util.Collection;
import java.util.Optional;

/// Exposes persistence-neutral field metadata shared by query and authorization contracts.
public interface FieldSchemaView {
    /// Resolves the effective static and runtime field allowlist for one context.
    @SuppressWarnings("NullableProblems")
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
}
