package io.taskmigo.web.adapter.in.http.support.objectauthorization;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.port.in.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.query.QuerySchemaView;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/// Resolves typed Object Authorization predicates against the handler's operation Query Schema.
@Component
public final class ObjectAuthorizationPredicateArgumentResolver implements HandlerMethodArgumentResolver {

    private final ObjectAuthorization authorization;
    private final List<QuerySchemaView> schemas;

    public ObjectAuthorizationPredicateArgumentResolver(
        ObjectAuthorization authorization,
        List<QuerySchemaView> schemas
    ) {
        this.authorization = authorization;
        this.schemas = List.copyOf(schemas);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == ObjectAuthorizationPredicate.class;
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        @Nullable ModelAndViewContainer container,
        NativeWebRequest webRequest,
        @Nullable WebDataBinderFactory binderFactory
    ) {
        QuerySchemaView schema = this.schema(parameter);
        Object value = webRequest.getAttribute(AuthorizationContext.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof AuthorizationContext context)) {
            throw new IllegalStateException("authorization context is missing for object access");
        }
        return this.authorization.authorize(context, schema);
    }

    private QuerySchemaView schema(MethodParameter parameter) {
        QueryOperation operation = parameter.getMethodAnnotation(QueryOperation.class);
        if (operation == null) {
            throw new IllegalStateException("ObjectAuthorizationPredicate handler must declare @QueryOperation");
        }
        List<QuerySchemaView> matches = this.schemas
            .stream()
            .filter(candidate -> candidate.operation().equals(operation.value()))
            .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one Object Authorization Query Schema for operation " +
                    operation.value() +
                    ", found " +
                    matches.size()
            );
        }
        return matches.getFirst();
    }
}
