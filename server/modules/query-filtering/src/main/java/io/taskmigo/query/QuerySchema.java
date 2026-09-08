package io.taskmigo.query;

import java.util.Collection;
import java.util.Optional;

/// Defines the persistence-neutral, explicitly allow-listed logical query surface for a contract type.
public interface QuerySchema<Q> {
    /// Returns the Java query-contract type represented by this schema.
    Class<Q> queryType();

    /// Resolves one explicitly registered API path.
    Optional<QueryField> field(QueryPath path);

    /// Returns every explicitly registered field.
    Collection<QueryField> fields();

    /// Returns a stable identity for paths, types, nullability, and operators.
    default String identity() {
        return this.queryType().getName() + ":" + this.fields().stream().map(Object::toString).sorted().toList();
    }
}
