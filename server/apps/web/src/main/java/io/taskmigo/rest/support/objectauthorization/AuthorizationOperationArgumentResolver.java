package io.taskmigo.rest.support.objectauthorization;

import io.taskmigo.auth.authorization.request.AuthorizationSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/// Resolves the operation snapshot transported from Spring Security into a typed MVC controller argument.
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
        HttpServletRequest request = Objects.requireNonNull(
            webRequest.getNativeRequest(HttpServletRequest.class),
            "authorization operation requires an HTTP servlet request"
        );
        Object value = request.getAttribute(AuthorizationOperation.SNAPSHOT_ATTRIBUTE);
        if (!(value instanceof AuthorizationSnapshot snapshot)) {
            throw new IllegalStateException("authorization snapshot is missing for object access");
        }
        return new AuthorizationOperation(snapshot, request.getMethod(), path(request));
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().split("\\?", 2)[0];
    }
}
