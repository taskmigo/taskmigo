package io.taskmigo.rest.support.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorization;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.request.AuthorizationContext;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

@ExtendWith(MockitoExtension.class)
class ObjectAuthorizationPredicateArgumentResolverTest {

    @Mock
    private ObjectAuthorization authorization;

    @Mock
    private ObjectAuthorizationSchema<TestObject> schema;

    @Mock
    private ObjectAuthorizationPredicate<TestObject> predicate;

    @Mock
    private AuthorizationContext context;

    /**
     * Verifies that a typed predicate handler argument is authorized with the matching resource schema and request context.
     *
     * Given: a handler parameter `ObjectAuthorizationPredicate<TestObject>`, its schema, and the current operation context.
     * Expect: the resolver delegates to Object Authorization and returns the resulting typed predicate.
     */
    @Test
    @DisplayName("resolves a typed object authorization predicate from the current request")
    void shouldResolvePredicateWhenHandlerDeclaresObjectType() throws NoSuchMethodException {
        // Arrange
        Method method = TestController.class.getDeclaredMethod("list", ObjectAuthorizationPredicate.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        when(this.schema.objectType()).thenReturn(TestObject.class);
        when(this.authorization.authorize(this.context, this.schema)).thenReturn(this.predicate);
        ObjectAuthorizationPredicateArgumentResolver resolver = new ObjectAuthorizationPredicateArgumentResolver(
            this.authorization,
            List.of(this.schema)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthorizationContext.ATTRIBUTE, this.context);

        // Act
        Object resolved = resolver.resolveArgument(parameter, null, new ServletWebRequest(request), null);

        // Assert
        assertThat(resolver.supportsParameter(parameter)).isTrue();
        assertThat(resolved).isSameAs(this.predicate);
    }

    private static final class TestController {

        void list(ObjectAuthorizationPredicate<TestObject> authorization) {}
    }

    private static final class TestObject {}
}
