package io.taskmigo.web.api.support.query;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilterByException;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchemaView;
import io.taskmigo.query.QuerySort;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/// Resolves FilteredQuery arguments against the Query Schema selected by the handler operation.
@Component
public final class FilteredQueryArgumentResolver implements HandlerMethodArgumentResolver {

    private final List<QuerySchemaView> schemas;
    private final FilterByCompiler filters;
    private final QueryFieldContextResolver contexts;

    /// Creates a resolver using Spring-managed operation schemas, filtering, and runtime field context resolution.
    public FilteredQueryArgumentResolver(
        List<QuerySchemaView> schemas,
        FilterByCompiler filters,
        QueryFieldContextResolver contexts
    ) {
        this.schemas = List.copyOf(schemas);
        this.filters = filters;
        this.contexts = contexts;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == FilteredQuery.class;
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        @Nullable ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        @Nullable WebDataBinderFactory binderFactory
    ) {
        QuerySchemaView schema = this.schema(parameter);
        QueryFieldContext context = this.contexts.resolve(schema.operation(), webRequest);
        List<QuerySort> sorting = sorting(schema, context, webRequest);
        return new FilteredQuery<>(
            this.filters.compile(schema, context, webRequest.getParameter("filterBy")),
            context,
            sorting
        );
    }

    private static List<QuerySort> sorting(
        QuerySchemaView schema,
        QueryFieldContext context,
        NativeWebRequest request
    ) {
        String sortBy = request.getParameter("sortBy");
        String sortOrder = request.getParameter("sortOrder");
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

        QuerySort.Direction direction = direction(sortOrder);
        return List.of(new QuerySort(path, direction));
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

    private QuerySchemaView schema(MethodParameter parameter) {
        QueryOperation operation = parameter.getMethodAnnotation(QueryOperation.class);
        if (operation == null) {
            throw new IllegalStateException("FilteredQuery handler must declare @QueryOperation");
        }
        List<QuerySchemaView> matches = this.schemas
            .stream()
            .filter(candidate -> candidate.operation().equals(operation.value()))
            .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one Query Schema for operation " + operation.value() + ", found " + matches.size()
            );
        }
        return matches.getFirst();
    }
}
