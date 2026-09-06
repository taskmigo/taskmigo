package io.taskmigo.rest.support.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.request.AuthorizationOperation;
import io.taskmigo.auth.authorization.request.AuthorizationSnapshot;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

class AuthorizationOperationArgumentResolverTest {

    private final AuthorizationOperationArgumentResolver resolver = new AuthorizationOperationArgumentResolver();

    /**
     * Verifies that MVC resolves the request attribute into the operation established by security.
     *
     * Given: an HTTP request with a transported operation containing a snapshot, method `GET`, and path `/api/v0/users`.
     * Expect: argument resolution returns the exact transported operation and snapshot.
     */
    @Test
    @DisplayName("resolves an authorization operation from the request attribute")
    void shouldResolveOperationWhenRequestContainsAuthorizationSnapshot() throws Exception {
        // Arrange
        AuthorizationSnapshot snapshot = snapshot();
        AuthorizationOperation transported = new AuthorizationOperation(snapshot, "GET", "/api/v0/users");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/v0/users");
        request.setAttribute(AuthorizationOperation.ATTRIBUTE, transported);

        // Act
        AuthorizationOperation operation = this.resolver.resolveArgument(
            parameter(AuthorizationOperation.class),
            new ModelAndViewContainer(),
            new ServletWebRequest(request),
            null
        );

        // Assert
        assertThat(operation).isSameAs(transported);
        assertThat(operation.snapshot()).isSameAs(snapshot);
        assertThat(operation.method()).isEqualTo("GET");
        assertThat(operation.path()).isEqualTo("/api/v0/users");
    }

    /**
     * Verifies that a controller cannot proceed with object authorization when Spring Security did not establish a snapshot.
     *
     * Given: an HTTP request without the authorization operation request attribute.
     * Expect: argument resolution fails with an explicit missing-snapshot error.
     */
    @Test
    @DisplayName("rejects an operation when the request snapshot is missing")
    void shouldRejectOperationWhenRequestSnapshotIsMissing() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();

        // Act + Assert
        assertThatThrownBy(() ->
            this.resolver.resolveArgument(
                parameter(AuthorizationOperation.class),
                new ModelAndViewContainer(),
                new ServletWebRequest(request),
                null
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("operation");
    }

    /**
     * Verifies that the resolver is limited to the explicit authorization operation controller contract.
     *
     * Given: controller parameters typed as `AuthorizationOperation` and `String`.
     * Expect: only the authorization operation parameter is supported.
     */
    @Test
    @DisplayName("supports only authorization operation parameters")
    void shouldSupportAuthorizationOperationWhenParameterHasAuthorizationType() throws Exception {
        // Arrange
        MethodParameter authorization = parameter(AuthorizationOperation.class);
        MethodParameter unrelated = parameter(String.class);

        // Act
        boolean supportsAuthorization = this.resolver.supportsParameter(authorization);
        boolean supportsUnrelated = this.resolver.supportsParameter(unrelated);

        // Assert
        assertThat(supportsAuthorization).isTrue();
        assertThat(supportsUnrelated).isFalse();
    }

    private static MethodParameter parameter(Class<?> type) throws Exception {
        String methodName = type == AuthorizationOperation.class ? "authorizationEndpoint" : "unrelatedEndpoint";
        Method method = AuthorizationOperationArgumentResolverTest.class.getDeclaredMethod(methodName, type);
        return new MethodParameter(method, 0);
    }

    private static void authorizationEndpoint(AuthorizationOperation ignored) {}

    private static void unrelatedEndpoint(String ignored) {}

    private static AuthorizationSnapshot snapshot() {
        return new AuthorizationSnapshot(UUID.randomUUID(), List.of(), List.of(), Map.of());
    }
}
