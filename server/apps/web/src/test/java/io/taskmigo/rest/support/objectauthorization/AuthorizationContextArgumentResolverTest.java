package io.taskmigo.rest.support.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.request.AuthorizationContext;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

class AuthorizationContextArgumentResolverTest {

    private final AuthorizationContextArgumentResolver resolver = new AuthorizationContextArgumentResolver();

    /**
     * Verifies that MVC resolves the opaque context established by security without exposing its implementation.
     *
     * Given: an HTTP request carrying an opaque AuthorizationContext request attribute.
     * Expect: argument resolution returns the exact context and supports no snapshot or operation type.
     */
    @Test
    @DisplayName("resolves an opaque authorization context from the request attribute")
    void shouldResolveContextWhenRequestContainsAuthorizationContext() throws Exception {
        // Arrange
        AuthorizationContext transported = new AuthorizationContext() {};
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthorizationContext.ATTRIBUTE, transported);

        // Act
        AuthorizationContext context = this.resolver.resolveArgument(
            parameter(AuthorizationContext.class),
            new ModelAndViewContainer(),
            new ServletWebRequest(request),
            null
        );

        // Assert
        assertThat(context).isSameAs(transported);
    }

    /**
     * Verifies that MVC refuses to invoke an object-authorized controller without a security context.
     *
     * Given: an HTTP request without the authorization context request attribute.
     * Expect: argument resolution fails with an explicit missing-context error.
     */
    @Test
    @DisplayName("rejects a context when the request attribute is missing")
    void shouldRejectContextWhenRequestAuthorizationContextIsMissing() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();

        // Act + Assert
        assertThatThrownBy(() ->
            this.resolver.resolveArgument(
                parameter(AuthorizationContext.class),
                new ModelAndViewContainer(),
                new ServletWebRequest(request),
                null
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("context");
    }

    /**
     * Verifies that the resolver exposes only the public opaque context controller contract.
     *
     * Given: controller parameters typed as AuthorizationContext and String.
     * Expect: only the AuthorizationContext parameter is supported.
     */
    @Test
    @DisplayName("supports only authorization context parameters")
    void shouldSupportContextWhenParameterHasAuthorizationContextType() throws Exception {
        // Arrange
        MethodParameter authorization = parameter(AuthorizationContext.class);
        MethodParameter unrelated = parameter(String.class);

        // Act
        boolean supportsAuthorization = this.resolver.supportsParameter(authorization);
        boolean supportsUnrelated = this.resolver.supportsParameter(unrelated);

        // Assert
        assertThat(supportsAuthorization).isTrue();
        assertThat(supportsUnrelated).isFalse();
    }

    private static MethodParameter parameter(Class<?> type) throws Exception {
        String methodName = type == AuthorizationContext.class ? "authorizationEndpoint" : "unrelatedEndpoint";
        Method method = AuthorizationContextArgumentResolverTest.class.getDeclaredMethod(methodName, type);
        return new MethodParameter(method, 0);
    }

    private static void authorizationEndpoint(AuthorizationContext ignored) {}

    private static void unrelatedEndpoint(String ignored) {}
}
