package io.taskmigo.web.adapter.in.http.support.query;

import io.taskmigo.language.ResourceSchema;
import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QueryBinding;
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

    private final List<QueryBinding<?>> bindings;
    private final List<ResourceSchema> schemas;
    private final FilterByCompiler filters;

    /// Creates a resolver using Spring-managed query bindings and semantic schemas.
    public FilteredQueryArgumentResolver(
        List<QueryBinding<?>> bindings,
        List<ResourceSchema> schemas,
        FilterByCompiler filters
    ) {
        this.bindings = List.copyOf(bindings);
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
        List<QueryBinding<?>> matches = this.bindings
            .stream()
            .filter(candidate -> candidate.queryType().equals(queryType))
            .toList();
        Class<?> declaredType = Objects.requireNonNull(queryType);
        if (matches.isEmpty()) {
            throw new IllegalStateException("No query binding registered for " + declaredType.getName());
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("multiple query bindings registered for " + declaredType.getName());
        }
        QueryBinding<?> binding = matches.getFirst();
        ResourceSchema schema = this.schemas
            .stream()
            .filter(candidate -> candidate.type().equals(binding.resourceType()))
            .filter(candidate -> candidate.fingerprint().equals(binding.schemaFingerprint()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No compatible resource schema registered for query binding"));
        return new FilteredQuery<>(this.filters.compileUntyped(schema, binding, webRequest.getParameter("filterBy")));
    }
}
