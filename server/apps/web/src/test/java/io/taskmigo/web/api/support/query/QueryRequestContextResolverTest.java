package io.taskmigo.web.api.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.query.QuerySchemaView;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(MockitoExtension.class)
class QueryRequestContextResolverTest {

    @Mock
    private QuerySchemaView schema;

    @Mock
    private QuerySchemaView otherSchema;

    /**
     * Verifies that the current HandlerMethod is the single source of truth for Query operation resolution.
     *
     * Given: a request mapped to a handler with @QueryOperation and one matching runtime context source.
     * Expect: the resolver selects that operation's schema and caches the exact runtime field snapshot for the request.
     */
    @Test
    @DisplayName("resolves and caches Query context from the current handler operation")
    void shouldResolveAndCacheQueryContextWhenCurrentHandlerDeclaresOperation() throws NoSuchMethodException {
        // Arrange
        Method method = TestController.class.getDeclaredMethod("list");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
            HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
            new HandlerMethod(new TestController(), method)
        );
        QueryFieldContext queryContext = new QueryFieldContext(Map.of("scope", "incident"));
        AtomicInteger sourceCalls = new AtomicInteger();
        QueryFieldContextSource source = new QueryFieldContextSource() {
            @Override
            public boolean supports(String operation) {
                return operation.equals("test.objects.list");
            }

            @Override
            public QueryFieldContext resolve(String operation, NativeWebRequest webRequest) {
                sourceCalls.incrementAndGet();
                return queryContext;
            }
        };
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.otherSchema.operation()).thenReturn("test.objects.delete");
        QueryRequestContextResolver resolver = new QueryRequestContextResolver(
            List.of(this.otherSchema, this.schema),
            new QueryFieldContextResolver(List.of(source)),
            request
        );

        // Act
        ResolvedQueryRequestContext first = resolver.resolve();
        ResolvedQueryRequestContext second = resolver.resolve();

        // Assert
        assertThat(first).isSameAs(second);
        assertThat(first.schema()).isSameAs(this.schema);
        assertThat(first.queryContext()).isSameAs(queryContext);
        assertThat(sourceCalls).hasValue(1);
    }

    private static final class TestController {

        @QueryOperation("test.objects.list")
        void list() {}
    }
}
