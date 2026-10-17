package io.taskmigo.web.api.support.query;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryOperation;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/// Exposes the cached server-owned runtime Query field context to handlers that must pass it into persistence.
@Component
public final class QueryFieldContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final QueryFieldContextResolver contexts;

    public QueryFieldContextArgumentResolver(QueryFieldContextResolver contexts) {
        this.contexts = contexts;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == QueryFieldContext.class;
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        @Nullable ModelAndViewContainer container,
        NativeWebRequest webRequest,
        @Nullable WebDataBinderFactory binderFactory
    ) {
        QueryOperation operation = parameter.getMethodAnnotation(QueryOperation.class);
        if (operation == null) {
            throw new IllegalStateException("QueryFieldContext handler must declare @QueryOperation");
        }
        return this.contexts.resolve(operation.value(), webRequest);
    }
}
