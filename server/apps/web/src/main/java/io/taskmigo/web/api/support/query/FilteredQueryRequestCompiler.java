package io.taskmigo.web.api.support.query;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilterByException;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.QuerySchemaView;
import io.taskmigo.query.QuerySort;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/// Compiles the public HTTP query model into Taskmigo's internal typed Query representation.
@Component
public final class FilteredQueryRequestCompiler {

    private final List<QuerySchemaView> schemas;
    private final FilterByCompiler filters;

    public FilteredQueryRequestCompiler(List<QuerySchemaView> schemas, FilterByCompiler filters) {
        this.schemas = List.copyOf(Objects.requireNonNull(schemas));
        this.filters = Objects.requireNonNull(filters);
    }

    public <Q> FilteredQuery<Q> compile(String operation, FilteredQueryRequest request, QueryFieldContext context) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(request);
        Objects.requireNonNull(context);
        QuerySchemaView schema = this.schema(operation);
        List<QuerySort> sorting = sorting(schema, context, request);
        QueryPredicate<Q> predicate = this.filters.compileTyped(schema, context, request.getFilterBy());
        return new FilteredQuery<>(predicate, context, sorting);
    }

    private static List<QuerySort> sorting(
        QuerySchemaView schema,
        QueryFieldContext context,
        FilteredQueryRequest request
    ) {
        String sortBy = request.getSortBy();
        String sortOrder = request.getSortOrder();
        if (sortBy == null || sortBy.isBlank()) {
            if (sortOrder != null && !sortOrder.isBlank()) {
                throw new FilterByException("sortOrder requires sortBy");
            }
            return List.of();
        }

        QueryPath path;
        try {
            path = QueryPath.parse(sortBy);
        } catch (IllegalArgumentException exception) {
            throw new FilterByException("Invalid sortBy field", exception);
        }
        QueryFieldDescriptor field = schema
            .field(path, context)
            .orElseThrow(() -> new FilterByException("Unknown sortBy field '" + sortBy + "'"));
        if (!field.sortable()) {
            throw new FilterByException("Field '" + sortBy + "' is not sortable");
        }

        return List.of(new QuerySort(path, direction(sortOrder)));
    }

    private static QuerySort.Direction direction(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return QuerySort.Direction.ASC;
        }
        try {
            return QuerySort.Direction.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new FilterByException("sortOrder must be ASC or DESC", exception);
        }
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
