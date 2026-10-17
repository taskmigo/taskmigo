package io.taskmigo.query;

import java.util.Objects;
import java.util.Set;

/// Describes one logical field exposed by an operation-scoped query schema.
public record QueryFieldDescriptor(
    QueryPath path,
    TypeDescriptor type,
    boolean nullable,
    Set<QueryOperator> operators,
    boolean sortable,
    String sourceIdentity
) {
    public QueryFieldDescriptor {
        Objects.requireNonNull(path);
        Objects.requireNonNull(type);
        operators = Set.copyOf(Objects.requireNonNull(operators));
        Objects.requireNonNull(sourceIdentity);
        if (sourceIdentity.isBlank()) {
            throw new IllegalArgumentException("Query field source identity must not be blank");
        }
    }

    /// Creates a descriptor whose persistence source identity is its logical path.
    public QueryFieldDescriptor(
        QueryPath path,
        TypeDescriptor type,
        boolean nullable,
        Set<QueryOperator> operators,
        boolean sortable
    ) {
        this(path, type, nullable, operators, sortable, path.text());
    }

    /// Creates a filter-only field descriptor for compatibility with existing static schemas.
    public QueryFieldDescriptor(QueryPath path, TypeDescriptor type, boolean nullable, Set<QueryOperator> operators) {
        this(path, type, nullable, operators, false, path.text());
    }

    /// Returns whether this field permits the supplied expression operator.
    public boolean supports(QueryOperator operator) {
        return this.operators.contains(operator);
    }
}
