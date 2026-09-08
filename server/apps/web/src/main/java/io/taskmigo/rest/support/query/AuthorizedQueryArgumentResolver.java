package io.taskmigo.rest.support.query;

import io.taskmigo.auth.authorization.object.ObjectAuthorization;
import io.taskmigo.auth.authorization.request.AuthorizationContext;
import io.taskmigo.auth.authorization.request.AuthorizationOperation;
import io.taskmigo.query.AuthorizedQuery;
import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.QueryPredicates;
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

/// Resolves generic AuthorizedQuery arguments from registered Query Schemas and request-scoped authorization.
@Component
public final class AuthorizedQueryArgumentResolver implements HandlerMethodArgumentResolver {
    private final List<QuerySchema<?>> schemas;
    private final FilterByCompiler filters;
    private final ObjectAuthorization objectAuthorization;

    /// Creates a resolver using Spring-managed schemas and authorization services.
    public AuthorizedQueryArgumentResolver(
        List<QuerySchema<?>> schemas,
        FilterByCompiler filters,
        ObjectAuthorization objectAuthorization
    ) {
        this.schemas = List.copyOf(schemas);
        this.filters = filters;
        this.objectAuthorization = objectAuthorization;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == AuthorizedQuery.class;
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
            .orElseThrow(() -> new IllegalStateException("No Query Schema registered for " + Objects.requireNonNull(queryType).getName()));
        QueryPredicate<?> client = this.filters.compileUntyped(schema, webRequest.getParameter("filterBy"));
        Object operation = webRequest.getAttribute(AuthorizationOperation.ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        QueryPredicate<?> authorized = operation instanceof AuthorizationContext context
            ? this.objectAuthorization.authorize(context, schema)
            : QueryPredicates.standard().alwaysTrue();
        QueryPredicate<?> combined = QueryPredicates.standard().andUntyped(authorized, client);
        return new AuthorizedQuery<>(combined);
    }
}
