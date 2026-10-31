package io.taskmigo.query;

import java.util.Comparator;
import java.util.stream.Collectors;

/// Exposes the persistence-neutral metadata of one query contract schema.
public interface QuerySchemaView extends FieldSchemaView {
    /// Returns the stable technical identity that owns this query schema.
    String operation();

    /// Returns a deterministic identity for the effective query schema, including opaque runtime source identity.
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
                    field.operators().stream().map(Enum::name).sorted().collect(Collectors.joining(",")) +
                    ":" +
                    field.sortable() +
                    ":" +
                    field.sourceIdentity()
            )
            .collect(Collectors.joining("|"));
        return this.operation() + "[" + fields + "]";
    }

    /// Returns the identity for a static-only context.
    default String identity() {
        return this.identity(QueryFieldContext.empty());
    }
}
