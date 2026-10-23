package io.taskmigo.eav.query;

import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.query.QueryOperator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/// Describes one unambiguous logical EAV field that can participate in database-side queries.
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
}
