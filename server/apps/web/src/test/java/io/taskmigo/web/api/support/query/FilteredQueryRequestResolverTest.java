package io.taskmigo.web.api.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.QuerySchemaView;
import java.lang.reflect.Method;
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
class FilteredQueryRequestResolverTest {

    @Mock
    private FilterByCompiler filters;

    @Mock
    private QuerySchemaView schema;

    @Mock
    private QueryPredicate<TestObject> predicate;

    /**
     * Verifies that controllers only pass the public HTTP query model while operation and context are resolved internally.
     *
     * Given: a current handler operation and an HTTP filterBy value.
     * Expect: the resolver compiles the query against the handler's schema and server-owned Query field context.
     */
    @Test
    @DisplayName("compiles HTTP query request from the current handler operation")
    void shouldCompileQueryWhenCurrentHandlerDeclaresOperation() throws NoSuchMethodException {
        // Arrange
        Method method = TestController.class.getDeclaredMethod("list");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(
            HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
            new HandlerMethod(new TestController(), method)
        );
        QueryFieldContext context = QueryFieldContext.empty();
        when(this.schema.operation()).thenReturn("test.objects.list");
        doReturn(this.predicate).when(this.filters).compileTyped(this.schema, context, "object.name == \"alice\"");
        FilteredQueryRequestCompiler compiler = new FilteredQueryRequestCompiler(List.of(this.schema), this.filters);
        QueryRequestContextResolver contexts = new QueryRequestContextResolver(
            List.of(this.schema),
            new QueryFieldContextResolver(List.of()),
            servletRequest
        );
        FilteredQueryRequestResolver resolver = new FilteredQueryRequestResolver(compiler, contexts);
        FilteredQueryRequest request = new FilteredQueryRequest();
        request.setFilterBy("object.name == \"alice\"");

        // Act
        FilteredQuery<TestObject> result = resolver.resolve(request);

        // Assert
        assertThat(result.predicate()).isSameAs(this.predicate);
        assertThat(result.context()).isSameAs(context);
        assertThat(result.sorting()).isEmpty();
        verify(this.filters).compileTyped(this.schema, context, "object.name == \"alice\"");
    }

    private static final class TestController {

        @QueryOperation("test.objects.list")
        void list() {}
    }

    private static final class TestObject {}
}
