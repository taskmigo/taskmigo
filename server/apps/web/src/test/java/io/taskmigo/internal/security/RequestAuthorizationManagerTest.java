package io.taskmigo.internal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.auth.authorization.request.AuthorizationOperation;
import io.taskmigo.auth.authorization.request.AuthorizationSnapshot;
import io.taskmigo.auth.authorization.request.RequestAuthorizationDecision;
import io.taskmigo.auth.authorization.request.RequestAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class RequestAuthorizationManagerTest {

    /**
     * Verifies that security transports the complete authorization operation together with named route variables.
     *
     * Given: a versioned API request with a matched `userId` path variable and a valid user JWT.
     * Expect: the operation contains the normalized target and the snapshot receives immutable `request.pathVariables.userId` input.
     */
    @Test
    @DisplayName("passes named route variables to request authorization")
    void shouldExposeRouteVariablesWhenRequestIsAuthorized() {
        // Arrange
        UUID userId = UUID.randomUUID();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v0/users/target/statements");
        RequestAuthorizationContext context = mock(RequestAuthorizationContext.class);
        when(context.getRequest()).thenReturn(request);
        when(context.getVariables()).thenReturn(Map.of("userId", "target"));
        AuthorizationSnapshot snapshot = snapshot(userId);
        RequestAuthorizationService authorization = mock(RequestAuthorizationService.class);
        when(authorization.snapshot(eq(userId), any())).thenReturn(snapshot);
        when(authorization.authorize(snapshot, "GET", "/api/v0/users/target/statements")).thenReturn(
            new RequestAuthorizationDecision(true)
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
        ArgumentCaptor<Map<String, ?>> roots = ArgumentCaptor.forClass(Map.class);
        verify(authorization).snapshot(eq(userId), roots.capture());
        Map<?, ?> requestRoot = Objects.requireNonNull((Map<?, ?>) roots.getValue().get("request"));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(decision.isGranted()).isTrue();
        softly.assertThat(requestRoot.get("path")).isEqualTo("/api/v0/users/target/statements");
        softly.assertThat(requestRoot.get("pathVariables")).isEqualTo(Map.of("userId", "target"));
        softly.assertAll();
        ArgumentCaptor<AuthorizationOperation> operation = ArgumentCaptor.forClass(AuthorizationOperation.class);
        verify(request).setAttribute(eq(AuthorizationOperation.ATTRIBUTE), operation.capture());
        assertThat(operation.getValue()).isEqualTo(
            new AuthorizationOperation(snapshot, "GET", "/api/v0/users/target/statements")
        );
    }

    /**
     * Verifies that a request authorization failure remains denied at the web boundary.
     *
     * Given: a valid JWT but a request service that denies the request.
     * Expect: the authorization manager returns a denied decision.
     */
    @Test
    @DisplayName("denies the request when request authorization denies")
    void shouldDenyRequestWhenAuthorizationServiceDenies() {
        // Arrange
        UUID userId = UUID.randomUUID();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v0/users");
        RequestAuthorizationContext context = new RequestAuthorizationContext(request, Map.of());
        AuthorizationSnapshot snapshot = snapshot(userId);
        RequestAuthorizationService authorization = mock(RequestAuthorizationService.class);
        when(authorization.snapshot(eq(userId), any())).thenReturn(snapshot);
        when(authorization.authorize(snapshot, "GET", "/api/v0/users")).thenReturn(
            new RequestAuthorizationDecision(false)
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
     * Verifies that security reuses the complete operation already transported on the request.
     *
     * Given: a request carrying an operation for a snapshot and target different from the raw request target.
     * Expect: authorization uses the transported operation and does not resolve or replace the snapshot.
     */
    @Test
    @DisplayName("reuses an authorization operation already on the request")
    void shouldReuseAuthorizationOperationWhenRequestAlreadyContainsOne() {
        // Arrange
        UUID userId = UUID.randomUUID();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v0/users");
        AuthorizationSnapshot snapshot = snapshot(userId);
        AuthorizationOperation operation = new AuthorizationOperation(snapshot, "POST", "/api/v0/roles");
        when(request.getAttribute(AuthorizationOperation.ATTRIBUTE)).thenReturn(operation);
        RequestAuthorizationContext context = new RequestAuthorizationContext(request, Map.of());
        RequestAuthorizationService authorization = mock(RequestAuthorizationService.class);
        when(authorization.authorize(snapshot, "POST", "/api/v0/roles")).thenReturn(
            new RequestAuthorizationDecision(true)
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
        assertThat(decision.isGranted()).isTrue();
        verify(authorization).authorize(snapshot, "POST", "/api/v0/roles");
        org.mockito.Mockito.verify(authorization, org.mockito.Mockito.never()).snapshot(any(), any());
        org.mockito.Mockito.verify(request, org.mockito.Mockito.never()).setAttribute(any(), any());
    }

    private static AuthorizationSnapshot snapshot(UUID userId) {
        return new AuthorizationSnapshot(userId, List.of(), List.of(), Map.of());
    }
}
