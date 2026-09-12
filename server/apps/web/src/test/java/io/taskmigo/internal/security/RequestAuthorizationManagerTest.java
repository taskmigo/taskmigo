package io.taskmigo.internal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.authorization.request.RequestAuthorization;
import io.taskmigo.authorization.request.RequestAuthorizationResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class RequestAuthorizationManagerTest {

    /**
     * Verifies that security delegates the available typed principal and request values to Request Authorization.
     *
     * Given: a versioned API request with a matched `userId` path variable and a valid user JWT.
     * Expect: the typed service receives those values and the manager transports only the opaque result context.
     */
    @Test
    @DisplayName("passes typed principal and request values to request authorization")
    void shouldAuthorizeTypedRequestWhenJwtAndRouteVariablesAreValid() {
        // Arrange
        UUID userId = UUID.randomUUID();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v0/users/target/statements");
        RequestAuthorizationContext context = mock(RequestAuthorizationContext.class);
        when(context.getRequest()).thenReturn(request);
        when(context.getVariables()).thenReturn(Map.of("userId", "target"));
        AuthorizationContext authorizationContext = new AuthorizationContext() {};
        RequestAuthorization authorization = mock(RequestAuthorization.class);
        when(authorization.authorize(any(), any())).thenReturn(
            new RequestAuthorizationResult(true, authorizationContext)
        );
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("principal_type", "user")
            .claim("user_id", userId.toString())
            .claim("principal_username", "alice")
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt,
            List.of(new SimpleGrantedAuthority("SCOPE_taskmigo.api")),
            "alice"
        );
        RequestAuthorizationManager manager = new RequestAuthorizationManager(authorization);

        // Act
        AuthorizationDecision decision = manager.authorize(() -> authentication, context);

        // Assert
        ArgumentCaptor<AuthorizationPrincipal> principal = ArgumentCaptor.forClass(AuthorizationPrincipal.class);
        ArgumentCaptor<AuthorizationRequest> authorizationRequest = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorization).authorize(principal.capture(), authorizationRequest.capture());
        assertThat(decision.isGranted()).isTrue();
        assertThat(principal.getValue()).isEqualTo(new AuthorizationPrincipal(userId, "alice"));
        assertThat(authorizationRequest.getValue()).isEqualTo(
            new AuthorizationRequest("GET", "/api/v0/users/target/statements", Map.of("userId", "target"))
        );
        verify(request).setAttribute(AuthorizationContext.ATTRIBUTE, authorizationContext);
    }

    /**
     * Verifies that a denied typed authorization result remains denied at the web security boundary.
     *
     * Given: a valid JWT and a RequestAuthorization service returning granted = false.
     * Expect: the authorization manager returns a denied decision.
     */
    @Test
    @DisplayName("denies the request when typed request authorization denies")
    void shouldDenyRequestWhenTypedAuthorizationDenies() {
        // Arrange
        UUID userId = UUID.randomUUID();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v0/users");
        RequestAuthorizationContext context = new RequestAuthorizationContext(request, Map.of());
        RequestAuthorization authorization = mock(RequestAuthorization.class);
        when(authorization.authorize(any(), any())).thenReturn(
            new RequestAuthorizationResult(false, new AuthorizationContext() {})
        );
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("principal_type", "user")
            .claim("user_id", userId.toString())
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt,
            List.of(new SimpleGrantedAuthority("SCOPE_taskmigo.api"))
        );
        RequestAuthorizationManager manager = new RequestAuthorizationManager(authorization);

        // Act
        AuthorizationDecision decision = manager.authorize(() -> authentication, context);

        // Assert
        assertThat(decision.isGranted()).isFalse();
    }

    /**
     * Verifies that a cached decision avoids a second typed authorization operation for one servlet request.
     *
     * Given: a request whose cached decision attribute is true.
     * Expect: the manager returns granted without invoking RequestAuthorization.
     */
    @Test
    @DisplayName("reuses the typed authorization result for one request")
    void shouldReuseAuthorizationResultWhenSecurityChecksTheRequestAgain() {
        // Arrange
        UUID userId = UUID.randomUUID();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v0/users");
        when(request.getAttribute("taskmigo.authorization.request.decision")).thenReturn(true);
        RequestAuthorizationContext context = new RequestAuthorizationContext(request, Map.of());
        RequestAuthorization authorization = mock(RequestAuthorization.class);
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("principal_type", "user")
            .claim("user_id", userId.toString())
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt,
            List.of(new SimpleGrantedAuthority("SCOPE_taskmigo.api"))
        );
        RequestAuthorizationManager manager = new RequestAuthorizationManager(authorization);

        // Act
        AuthorizationDecision decision = manager.authorize(() -> authentication, context);

        // Assert
        assertThat(decision.isGranted()).isTrue();
        verify(authorization, Mockito.never()).authorize(any(), any());
    }
}
