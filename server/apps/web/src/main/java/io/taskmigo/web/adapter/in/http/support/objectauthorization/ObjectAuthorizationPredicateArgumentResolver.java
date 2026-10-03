package io.taskmigo.web.adapter.in.http.support.objectauthorization;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.port.in.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
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
    private final List<ObjectAuthorizationBinding<?>> bindings;

    /// Creates a resolver using the Object Authorization service and resource-owned bindings.
    public ObjectAuthorizationPredicateArgumentResolver(
        ObjectAuthorization authorization,
        List<ObjectAuthorizationBinding<?>> bindings
    ) {
        this.authorization = authorization;
        this.bindings = List.copyOf(bindings);
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
        List<ObjectAuthorizationBinding<?>> matches = this.bindings
            .stream()
            .filter(candidate -> candidate.objectType().equals(objectType))
            .toList();
        if (matches.isEmpty()) {
            throw new IllegalStateException("No Object Authorization binding registered for " + objectType.getName());
        }
        if (matches.size() != 1) {
            throw new IllegalStateException(
                "multiple Object Authorization bindings registered for " + objectType.getName()
            );
        }
        ObjectAuthorizationBinding<?> binding = matches.getFirst();
        Object value = webRequest.getAttribute(AuthorizationContext.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof AuthorizationContext context)) {
            throw new IllegalStateException("authorization context is missing for object access");
        }
        return this.authorization.authorize(context, binding);
    }
}
