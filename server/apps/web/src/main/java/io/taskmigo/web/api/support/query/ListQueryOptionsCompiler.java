package io.taskmigo.web.api.support.query;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.ListQuerySpec;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.QuerySchemaView;
import io.taskmigo.query.QuerySort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/// Compiles the public HTTP query model into Taskmigo's internal typed Query representation.
@Component
public final class ListQueryOptionsCompiler {

    private final List<QuerySchemaView> schemas;
    private final FilterByCompiler filters;

    public ListQueryOptionsCompiler(List<QuerySchemaView> schemas, FilterByCompiler filters) {
        this.schemas = List.copyOf(Objects.requireNonNull(schemas));
        this.filters = Objects.requireNonNull(filters);
    }

    public <Q> ListQuerySpec<Q> compile(String operation, ListQueryOptions request, QueryFieldContext context) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(request);
        Objects.requireNonNull(context);
        QuerySchemaView schema = this.schema(operation);
        List<QuerySort> sorting = sorting(schema, context, request);
        QueryPredicate<Q> predicate = this.filters.compileTyped(schema, context, request.getFilterBy());
        return new ListQuerySpec<>(predicate, context, sorting);
    }

    private static List<QuerySort> sorting(
        QuerySchemaView schema,
        QueryFieldContext context,
        ListQueryOptions request
    ) {
        List<String> requested = List.copyOf(request.getSortBy());
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

    private QuerySchemaView schema(String operation) {
        List<QuerySchemaView> matches = this.schemas
            .stream()
            .filter(candidate -> candidate.operation().equals(operation))
            .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one Query Schema for operation " + operation + ", found " + matches.size()
            );
        }
        return matches.getFirst();
    }
}
