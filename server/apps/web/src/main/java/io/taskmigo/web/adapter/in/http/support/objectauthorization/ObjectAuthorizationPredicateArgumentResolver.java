package io.taskmigo.web.adapter.in.http.support.objectauthorization;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.port.in.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QuerySchemaView;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/// Resolves typed Object Authorization predicates from the current operation context.
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
        Class<?> objectType = ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve();
        if (objectType == null) {
            throw new IllegalStateException("ObjectAuthorizationPredicate must declare an object type");
        }
        QuerySchemaView schema = this.schemas
            .stream()
            .filter(candidate -> candidate.operation().equals(objectType.getName()))
            .findFirst()
            .orElseThrow(() ->
                new IllegalStateException("No Object Authorization Query Schema registered for " + objectType.getName())
            );
        Object value = webRequest.getAttribute(AuthorizationContext.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof AuthorizationContext context)) {
            throw new IllegalStateException("authorization context is missing for object access");
        }
        return this.authorization.authorize(context, schema);
    }
}
