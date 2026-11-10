package io.taskmigo.authorization.object;

import io.taskmigo.query.FieldSchemaView;
import io.taskmigo.query.QueryFieldContext;
import java.util.Comparator;
import java.util.stream.Collectors;

/// Defines the object fields and operators available to one Object Authorization contract.
public interface ObjectAuthorizationSchema<Q> extends FieldSchemaView {
    /// Returns a deterministic identity for the effective authorization schema.
    ///
    /// Sorting is intentionally excluded because it is a Query capability, not an Authorization capability.
    default String authorizationIdentity(QueryFieldContext context) {
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
                    field.sourceIdentity()
            )
            .collect(Collectors.joining("|"));
        return this.getClass().getName() + "[" + fields + "]";
    }

    default String authorizationIdentity() {
        return this.authorizationIdentity(QueryFieldContext.empty());
    }
}
