package io.taskmigo.rest.support.query;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QuerySchema;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/// Resolves generic FilteredQuery arguments from registered Query Schemas and client filterBy input.
@Component
public final class FilteredQueryArgumentResolver implements HandlerMethodArgumentResolver {
    private final List<QuerySchema<?>> schemas;
    private final FilterByCompiler filters;

    /// Creates a resolver using Spring-managed Query Schemas and the filter compiler.
    public FilteredQueryArgumentResolver(List<QuerySchema<?>> schemas, FilterByCompiler filters) {
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
        ResolvableType type = ResolvableType.forMethodParameter(parameter).getGeneric(0);
        Class<?> queryType = type.resolve();
        QuerySchema<?> schema = this.schemas.stream()
            .filter(candidate -> candidate.queryType().equals(queryType))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No Query Schema registered for " + Objects.requireNonNull(queryType).getName()
            ));
        return new FilteredQuery<>(this.filters.compileUntyped(schema, webRequest.getParameter("filterBy")));
    }
}
