package io.taskmigo.web.api.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.ListQuerySpec;
import io.taskmigo.query.QueryContract;
import io.taskmigo.query.QueryFieldContext;
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
class ListQueryResolverTest {

    @Mock
    private FilterByCompiler filters;

    @Mock
    private TestQueryContract schema;

    @Mock
    private QueryPredicate<TestObject> predicate;

    @Test
    @DisplayName("compiles HTTP query request from the handler's typed Query Contract")
    void shouldCompileQueryWhenCurrentHandlerDeclaresContract() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(
            HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
            new HandlerMethod(new TestController(), method)
        );
        QueryFieldContext context = QueryFieldContext.empty();
        doReturn(this.predicate).when(this.filters).compileTyped(this.schema, context, "object.name == \"alice\"");
        ListQueryOptionsCompiler compiler = new ListQueryOptionsCompiler(this.filters);
        ListQueryResolver resolver = new ListQueryResolver(
            compiler,
            List.of(this.schema),
            new QueryFieldContextResolver(List.of()),
            servletRequest
        );
        ListQueryOptions request = new ListQueryOptions();
        request.setFilterBy("object.name == \"alice\"");

        ListQuerySpec<TestObject> result = resolver.resolve(request);

        assertThat(result.predicate()).isSameAs(this.predicate);
        assertThat(result.context()).isSameAs(context);
        assertThat(result.sorting()).isEmpty();
        verify(this.filters).compileTyped(this.schema, context, "object.name == \"alice\"");
    }

    private static final class TestController {

        @QueryContract(TestQueryContract.class)
        void list() {}
    }

    private interface TestQueryContract extends QuerySchemaView {}

    private static final class TestObject {}
}
