package io.taskmigo.web.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
final class VersionedApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final List<ApiSecurityErrorRenderer> renderers;

    VersionedApiSecurityErrorHandler(List<ApiSecurityErrorRenderer> renderers) {
        this.renderers = renderers;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException, ServletException {
        this.write(request, response, HttpStatus.UNAUTHORIZED, "security.unauthorized", "Authentication is required");
    }

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        org.springframework.security.access.AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        this.write(request, response, HttpStatus.FORBIDDEN, "security.forbidden", "Access is forbidden");
    }

    private void write(
        HttpServletRequest request,
        HttpServletResponse response,
        HttpStatus status,
        String messageCode,
        String messageText
    ) throws IOException {
        for (ApiSecurityErrorRenderer renderer : this.renderers) {
            if (renderer.supports(request)) {
                renderer.write(response, status, messageCode, messageText);
                return;
            }
        }
        response.sendError(status.value(), messageText);
    }
}
