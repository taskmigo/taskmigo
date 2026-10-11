package io.taskmigo.web.adapter.in.http.support.query;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.query.QuerySchemaView;
import java.util.List;
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

    /// Creates a resolver using Spring-managed operation schema views and the filter compiler.
    public FilteredQueryArgumentResolver(List<QuerySchemaView> schemas, FilterByCompiler filters) {
        this.schemas = List.copyOf(schemas);
        this.filters = filters;
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
        return new FilteredQuery<>(this.filters.compile(schema, webRequest.getParameter("filterBy")));
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
