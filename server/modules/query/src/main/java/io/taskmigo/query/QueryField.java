package io.taskmigo.query;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.language.FieldId;
import java.util.Objects;
import java.util.Set;

/// Temporary migration carrier for existing application declarations; it is removed after binding migration.
public record QueryField(QueryPath path, TypeDescriptor type, boolean nullable, Set<QueryOperator> operators) {
    public QueryField {
        Objects.requireNonNull(path);
        Objects.requireNonNull(type);
        operators = Set.copyOf(operators);
    }

    public QueryField(QueryPath path, TypeDescriptor type, boolean nullable) {
        this(
            path,
            type,
            nullable,
            Set.of(QueryOperator.EQ, QueryOperator.NE, QueryOperator.GT, QueryOperator.GE, QueryOperator.LT, QueryOperator.LE, QueryOperator.IN)
        );
    }

    /// Returns the stable semantic field identity for an owning resource type.
    public FieldId id(String resourceType) {
        return FieldId.of("field:" + resourceType + ":" + this.path.text());
    }
}
