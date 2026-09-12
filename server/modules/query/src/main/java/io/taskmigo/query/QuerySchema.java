package io.taskmigo.query;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/// Defines the persistence-neutral, explicitly allow-listed logical query surface for a contract type.
public interface QuerySchema<Q> {
    /// Returns the Java query-contract type represented by this schema.
    Class<Q> queryType();

    /// Resolves one explicitly registered API path.
    @SuppressWarnings("NullableProblems")
    Optional<QueryField> field(QueryPath path);

    /// Returns every explicitly registered field.
    Collection<QueryField> fields();

    /// Returns a stable identity for paths, types, nullability, and operators.
    default String identity() {
        return (
            this.queryType().getName() +
            ":" +
            this.fields()
                .stream()
                .map(QuerySchema::canonicalField)
                .sorted()
                .collect(Collectors.joining("|", "[", "]"))
        );
    }

    private static String canonicalField(QueryField field) {
        return (
            field.path().text() +
            ":" +
            field.type().getType().getTypeName() +
            ":" +
            field.nullable() +
            ":" +
            field
                .operators()
                .stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(",", "[", "]"))
        );
    }
}
