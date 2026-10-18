package io.taskmigo.web.api.support.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.query.QuerySchemaView;
import io.taskmigo.web.api.support.query.QueryFieldContextResolver;
import io.taskmigo.web.api.support.query.QueryRequestContextResolver;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(MockitoExtension.class)
class ObjectAuthorizationResolverTest {

    @Mock
    private ObjectAuthorization authorization;

    @Mock
    private QuerySchemaView schema;

    @Mock
    private ObjectAuthorizationPredicate<TestObject> predicate;

    @Mock
    private AuthorizationContext authorizationContext;

    /**
     * Verifies that Object Authorization remains server-derived after it is removed from controller parameters.
     *
     * Given: client-controlled authorization query/body values and a server-owned AuthorizationContext attribute.
     * Expect: only the server context and current handler Query operation determine the resolved predicate.
     */
    @Test
    @DisplayName("ignores client authorization data and resolves predicate from server request context")
    void shouldIgnoreClientAuthorizationWhenResolvingCurrentHandlerPredicate() throws NoSuchMethodException {
        // Arrange
        Method method = TestController.class.getDeclaredMethod("list");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
            HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
            new HandlerMethod(new TestController(), method)
        );
        request.setAttribute(AuthorizationContext.ATTRIBUTE, this.authorizationContext);
        request.setParameter("authorization", "client-allow-all");
        request.setContentType("application/json");
        request.setContent("{\"authorization\":\"client-allow-all\"}".getBytes(StandardCharsets.UTF_8));
        QueryFieldContext queryContext = QueryFieldContext.empty();
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.authorization.<TestObject>authorize(this.authorizationContext, this.schema, queryContext)).thenReturn(
            this.predicate
        );
        QueryRequestContextResolver queryContexts = new QueryRequestContextResolver(
            List.of(this.schema),
            new QueryFieldContextResolver(List.of()),
            request
        );
        ObjectAuthorizationResolver resolver = new ObjectAuthorizationResolver(
            this.authorization,
            queryContexts,
            request
        );

        // Act
        ResolvedObjectAuthorization<TestObject> resolved = resolver.resolve();

        // Assert
        assertThat(resolved.predicate()).isSameAs(this.predicate);
        assertThat(resolved.queryContext()).isSameAs(queryContext);
        verify(this.authorization).authorize(this.authorizationContext, this.schema, queryContext);
    }

    private static final class TestController {

        @QueryOperation("test.objects.list")
        void list() {}
    }

    private static final class TestObject {}
}
