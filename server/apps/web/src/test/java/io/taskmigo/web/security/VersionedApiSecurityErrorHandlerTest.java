package io.taskmigo.web.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class VersionedApiSecurityErrorHandlerTest {

    @Test
    @DisplayName("dispatches unauthorized failures to the first matching renderer")
    void shouldUseFirstMatchingRendererForUnauthorizedFailure() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ApiSecurityErrorRenderer first = mock(ApiSecurityErrorRenderer.class);
        ApiSecurityErrorRenderer matching = mock(ApiSecurityErrorRenderer.class);
        ApiSecurityErrorRenderer later = mock(ApiSecurityErrorRenderer.class);
        when(first.supports(request)).thenReturn(false);
        when(matching.supports(request)).thenReturn(true);
        VersionedApiSecurityErrorHandler handler = new VersionedApiSecurityErrorHandler(
            List.of(first, matching, later)
        );

        handler.commence(request, response, new BadCredentialsException("bad credentials"));

        verify(first).supports(request);
        verify(matching).supports(request);
        verify(matching).write(
            response,
            HttpStatus.UNAUTHORIZED,
            "security.unauthorized",
            "Authentication is required"
        );
        verify(later, never()).supports(request);
    }

    @Test
    @DisplayName("falls back to servlet errors when no renderer matches")
    void shouldSendServletErrorForForbiddenFailureWhenNoRendererMatches() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ApiSecurityErrorRenderer first = mock(ApiSecurityErrorRenderer.class);
        ApiSecurityErrorRenderer second = mock(ApiSecurityErrorRenderer.class);
        when(first.supports(request)).thenReturn(false);
        when(second.supports(request)).thenReturn(false);
        VersionedApiSecurityErrorHandler handler = new VersionedApiSecurityErrorHandler(List.of(first, second));

        handler.handle(request, response, new AccessDeniedException("denied"));

        verify(response).sendError(HttpStatus.FORBIDDEN.value(), "Access is forbidden");
    }
}
