package io.taskmigo.web.adapter.in.http.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilteredQuery;
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
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

@ExtendWith(MockitoExtension.class)
class FilteredQueryArgumentResolverTest {

    @Mock
    private FilterByCompiler filters;

    @Mock
    private QuerySchemaView schema;

    @Mock
    private QuerySchemaView otherSchema;

    @Mock
    private QueryPredicate<TestObject> predicate;

    /**
     * Given: two operation schemas can represent the same handler DTO and the handler declares one operation ID.
     * Expect: filterBy compiles only against the schema selected by the declared operation ID.
     */
    @Test
    @DisplayName("selects filter schema by handler operation")
    void shouldSelectSchemaWhenHandlerDeclaresOperation() throws NoSuchMethodException {
        // Arrange
        Method method = TestController.class.getDeclaredMethod("list", FilteredQuery.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.otherSchema.operation()).thenReturn("test.objects.delete");
        doReturn(this.predicate).when(this.filters).compile(this.schema, "object.name == \"alice\"");
        FilteredQueryArgumentResolver resolver = new FilteredQueryArgumentResolver(
            List.of(this.otherSchema, this.schema),
            this.filters
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("filterBy", "object.name == \"alice\"");

        // Act
        Object resolved = resolver.resolveArgument(parameter, null, new ServletWebRequest(request), null);

        // Assert
        assertThat(resolved).isInstanceOfSatisfying(FilteredQuery.class, query ->
            assertThat(query.predicate()).isSameAs(this.predicate)
        );
        verify(this.filters).compile(this.schema, "object.name == \"alice\"");
    }

    private static final class TestController {

        @QueryOperation("test.objects.list")
        void list(FilteredQuery<TestObject> filter) {}
    }

    private static final class TestObject {}
}
