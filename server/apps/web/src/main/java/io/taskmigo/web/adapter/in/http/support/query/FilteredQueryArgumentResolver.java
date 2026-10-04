package io.taskmigo.web.adapter.in.http.support.query;

import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.language.SchemaContext;
import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QueryBindingResolver;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/// Resolves generic FilteredQuery arguments through the effective runtime resource schema and execution binding.
@Component
public final class FilteredQueryArgumentResolver implements HandlerMethodArgumentResolver {

    private final QueryBindingResolver bindings;
    private final FilterByCompiler filters;

    /// Creates a resolver using the application-level runtime binding resolver.
    public FilteredQueryArgumentResolver(QueryBindingResolver bindings, FilterByCompiler filters) {
        this.bindings = Objects.requireNonNull(bindings);
        this.filters = Objects.requireNonNull(filters);
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
        Class<?> queryType = Objects.requireNonNull(
            ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve(),
            "FilteredQuery must declare a query type"
        );
        QueryBindingResolver.Resolution resolution = this.bindings.resolve(queryType, schemaContext(webRequest));
        return new FilteredQuery<>(
            this.filters.compileUntyped(resolution.schema(), resolution.binding(), webRequest.getParameter("filterBy"))
        );
    }

    private static SchemaContext schemaContext(NativeWebRequest webRequest) {
        Object value = webRequest.getAttribute(AuthorizationContext.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof AuthorizationContext context ? context.schemaContext() : SchemaContext.EMPTY;
    }
}
