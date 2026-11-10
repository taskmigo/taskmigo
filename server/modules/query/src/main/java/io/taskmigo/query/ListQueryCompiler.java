package io.taskmigo.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Compiles transport-neutral list-query intent against an explicit operation schema and runtime field context.
public final class ListQueryCompiler {

    private final FilterByCompiler filters;

    public ListQueryCompiler() {
        this(new FilterByCompiler());
    }

    public ListQueryCompiler(FilterByCompiler filters) {
        this.filters = Objects.requireNonNull(filters);
    }

    public <Q> ListQuerySpec<Q> compile(QuerySchemaView schema, QueryFieldContext context, ListQueryRequest request) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(context);
        Objects.requireNonNull(request);
        List<QuerySort> sorting = sorting(schema, context, request.sortBy());
        QueryPredicate<Q> predicate = this.filters.compileTyped(schema, context, request.filterBy());
        return new ListQuerySpec<>(predicate, context, sorting);
    }

    private static List<QuerySort> sorting(QuerySchemaView schema, QueryFieldContext context, List<String> requested) {
        if (requested.isEmpty()) {
            return List.of();
        }

        ArrayList<QuerySort> sorting = new ArrayList<>(requested.size());
        Set<QueryPath> seen = new HashSet<>();
        for (String value : requested) {
            sorting.add(sort(schema, context, value, seen));
        }
        return List.copyOf(sorting);
    }

    private static QuerySort sort(
        QuerySchemaView schema,
        QueryFieldContext context,
        String value,
        Set<QueryPath> seen
    ) {
        if (value.isBlank()) {
            throw new SortByException("sortBy field must not be blank");
        }
        if (value.startsWith("+")) {
            throw new SortByException("sortBy field must not use the '+' prefix");
        }

        QuerySort.Direction direction = value.startsWith("-") ? QuerySort.Direction.DESC : QuerySort.Direction.ASC;
        String fieldName = direction == QuerySort.Direction.DESC ? value.substring(1) : value;
        if (fieldName.isBlank()) {
            throw new SortByException("sortBy field must not be blank");
        }

        QueryPath path;
        try {
            path = QueryPath.parse(fieldName);
        } catch (IllegalArgumentException exception) {
            throw new SortByException("Invalid sortBy field '" + value + "'", exception);
        }
        if (!seen.add(path)) {
            throw new SortByException("Duplicate sortBy field '" + fieldName + "'");
        }

        QueryFieldDescriptor field = schema
            .field(path, context)
            .orElseThrow(() -> new SortByException("Unknown sortBy field '" + fieldName + "'"));
        if (!field.sortable()) {
            throw new SortByException("Field '" + fieldName + "' is not sortable");
        }
        return new QuerySort(path, direction);
    }
}
