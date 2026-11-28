package io.taskmigo.eav.query;

import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.query.QueryOperator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/// Describes a stable typed EAV identity for database-side queries.
public record EavQueryField(
    UUID fieldId,
    String key,
    EavDataType dataType,
    boolean nullable,
    Set<QueryOperator> operators,
    boolean sortable
) {
    public EavQueryField {
        Objects.requireNonNull(fieldId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(dataType);
        operators = Set.copyOf(Objects.requireNonNull(operators));
    }

    /// Derives the supported operators and sorting capability from the field's immutable value type.
    public static EavQueryField describe(UUID id, String key, EavDataType type, boolean nullable) {
        return switch (type) {
            case STRING -> new EavQueryField(
                id,
                key,
                type,
                nullable,
                Set.of(QueryOperator.EQ, QueryOperator.NE, QueryOperator.CONTAINS),
                true
            );
            case NUMBER, DATE, DATETIME -> new EavQueryField(
                id,
                key,
                type,
                nullable,
                Set.of(
                    QueryOperator.EQ,
                    QueryOperator.NE,
                    QueryOperator.GT,
                    QueryOperator.GTE,
                    QueryOperator.LT,
                    QueryOperator.LTE
                ),
                true
            );
            case BOOLEAN, UUID, ENUM -> new EavQueryField(
                id,
                key,
                type,
                nullable,
                Set.of(QueryOperator.EQ, QueryOperator.NE),
                false
            );
        };
    }
}
