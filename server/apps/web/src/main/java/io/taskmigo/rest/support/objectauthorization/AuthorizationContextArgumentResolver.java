package io.taskmigo.rest.support.objectauthorization;

import io.taskmigo.authorization.request.AuthorizationContext;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/// Resolves the opaque authorization context established by Spring Security into a controller argument.
@Component
public final class AuthorizationContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == AuthorizationContext.class;
    }

    @Override
    public AuthorizationContext resolveArgument(
        MethodParameter parameter,
        @Nullable ModelAndViewContainer container,
        NativeWebRequest webRequest,
        @Nullable WebDataBinderFactory binderFactory
    ) {
        Object value = webRequest.getAttribute(AuthorizationContext.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof AuthorizationContext context)) {
            throw new IllegalStateException("authorization context is missing for object access");
        }
        return context;
    }
}
