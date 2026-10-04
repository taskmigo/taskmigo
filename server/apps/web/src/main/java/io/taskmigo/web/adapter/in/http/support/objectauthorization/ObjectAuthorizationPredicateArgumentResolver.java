package io.taskmigo.web.adapter.in.http.support.objectauthorization;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationBindingResolver;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.port.in.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
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

/// Resolves typed Object Authorization predicates from the current operation and its effective runtime schema.
@Component
public final class ObjectAuthorizationPredicateArgumentResolver implements HandlerMethodArgumentResolver {

    private final ObjectAuthorization authorization;
    private final ObjectAuthorizationBindingResolver bindings;

    /// Creates a resolver using Object Authorization and the application-level runtime binding resolver.
    public ObjectAuthorizationPredicateArgumentResolver(
        ObjectAuthorization authorization,
        ObjectAuthorizationBindingResolver bindings
    ) {
        this.authorization = Objects.requireNonNull(authorization);
        this.bindings = Objects.requireNonNull(bindings);
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
        Object value = webRequest.getAttribute(AuthorizationContext.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof AuthorizationContext context)) {
            throw new IllegalStateException("authorization context is missing for object access");
        }
        ObjectAuthorizationBinding<?> binding = this.bindings.resolve(objectType, context.schemaContext());
        return this.authorization.authorize(context, binding);
    }
}
