package io.taskmigo.rest.support.objectauthorization;

import io.taskmigo.auth.authorization.request.AuthorizationOperation;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/// Resolves the complete authorization operation transported from Spring Security into a typed MVC controller argument.
@Component
public final class AuthorizationOperationArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == AuthorizationOperation.class;
    }

    @Override
    public AuthorizationOperation resolveArgument(
        MethodParameter parameter,
        @Nullable ModelAndViewContainer container,
        NativeWebRequest webRequest,
        @Nullable WebDataBinderFactory binderFactory
    ) {
        Object value = webRequest.getAttribute(AuthorizationOperation.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof AuthorizationOperation operation)) {
            throw new IllegalStateException("authorization operation is missing for object access");
        }
        return operation;
    }
}
