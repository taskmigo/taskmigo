package io.taskmigo.web.api.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilterByException;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.QuerySchemaView;
import io.taskmigo.query.QuerySort;
import io.taskmigo.query.TypeDescriptor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
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

    @Test
    @DisplayName("selects one operation schema and compiles filterBy with the cached runtime context")
    void shouldUseOperationRuntimeContextWhenCompilingFilter() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list", FilteredQuery.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        QueryFieldContext context = new QueryFieldContext(Map.of("type", "incident"));
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.otherSchema.operation()).thenReturn("test.objects.delete");
        doReturn(this.predicate).when(this.filters).compile(this.schema, context, "object.name == \"alice\"");
        AtomicInteger sourceCalls = new AtomicInteger();
        QueryFieldContextSource source = source("test.objects.list", context, sourceCalls);
        QueryFieldContextResolver contexts = new QueryFieldContextResolver(List.of(source));
        FilteredQueryArgumentResolver resolver = new FilteredQueryArgumentResolver(
            List.of(this.otherSchema, this.schema),
            this.filters,
            contexts
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("filterBy", "object.name == \"alice\"");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        Object first = resolver.resolveArgument(parameter, null, webRequest, null);
        Object second = resolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(first).isInstanceOfSatisfying(FilteredQuery.class, query -> {
            assertThat(query.predicate()).isSameAs(this.predicate);
            assertThat(query.context()).isEqualTo(context);
            assertThat(query.sorting()).isEmpty();
        });
        assertThat(second).isInstanceOf(FilteredQuery.class);
        assertThat(sourceCalls).hasValue(1);
        verify(this.filters, times(2)).compile(this.schema, context, "object.name == \"alice\"");
    }

    @Test
    @DisplayName("runtime context defaults empty and rejects multiple matching sources")
    void shouldResolveZeroOrOneRuntimeContextSource() {
        QueryFieldContextResolver empty = new QueryFieldContextResolver(List.of());
        NativeWebRequest emptyRequest = new ServletWebRequest(new MockHttpServletRequest());

        assertThat(empty.resolve("test.objects.list", emptyRequest)).isEqualTo(QueryFieldContext.empty());

        QueryFieldContext context = new QueryFieldContext(Map.of("scope", "one"));
        QueryFieldContextResolver duplicate = new QueryFieldContextResolver(
            List.of(
                source("test.objects.list", context, new AtomicInteger()),
                source("test.objects.list", context, new AtomicInteger())
            )
        );
        NativeWebRequest duplicateRequest = new ServletWebRequest(new MockHttpServletRequest());
        assertThatThrownBy(() -> duplicate.resolve("test.objects.list", duplicateRequest))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("multiple");
    }

    @Test
    @DisplayName("sortBy defaults ascending and accepts an explicit descending direction")
    void shouldResolveValidSorting() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list", FilteredQuery.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        QueryFieldContext context = QueryFieldContext.empty();
        QueryFieldDescriptor name = new QueryFieldDescriptor(
            QueryPath.of("name"),
            TypeDescriptor.of(String.class),
            false,
            Set.of(QueryOperator.EQ),
            true
        );
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.schema.field(QueryPath.of("name"), context)).thenReturn(Optional.of(name));
        doReturn(this.predicate).when(this.filters).compile(this.schema, context, null);
        FilteredQueryArgumentResolver resolver = new FilteredQueryArgumentResolver(
            List.of(this.schema),
            this.filters,
            new QueryFieldContextResolver(List.of())
        );

        MockHttpServletRequest ascending = new MockHttpServletRequest();
        ascending.setParameter("sortBy", "name");
        FilteredQuery<?> defaultSort = (FilteredQuery<?>) resolver.resolveArgument(
            parameter,
            null,
            new ServletWebRequest(ascending),
            null
        );

        MockHttpServletRequest descending = new MockHttpServletRequest();
        descending.setParameter("sortBy", "name");
        descending.setParameter("sortOrder", "desc");
        FilteredQuery<?> explicitSort = (FilteredQuery<?>) resolver.resolveArgument(
            parameter,
            null,
            new ServletWebRequest(descending),
            null
        );

        assertThat(defaultSort.sorting()).containsExactly(new QuerySort(QueryPath.of("name"), QuerySort.Direction.ASC));
        assertThat(explicitSort.sorting()).containsExactly(
            new QuerySort(QueryPath.of("name"), QuerySort.Direction.DESC)
        );
    }

    @Test
    @DisplayName(
        "sorting rejects direction without field, malformed direction, unknown fields, and non-sortable fields"
    )
    void shouldRejectInvalidSorting() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list", FilteredQuery.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        QueryFieldContext context = QueryFieldContext.empty();
        QueryFieldDescriptor notSortable = new QueryFieldDescriptor(
            QueryPath.of("secret"),
            TypeDescriptor.of(String.class),
            false,
            Set.of(QueryOperator.EQ),
            false
        );
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.schema.field(QueryPath.of("secret"), context)).thenReturn(Optional.of(notSortable));
        FilteredQueryArgumentResolver resolver = new FilteredQueryArgumentResolver(
            List.of(this.schema),
            this.filters,
            new QueryFieldContextResolver(List.of())
        );

        assertInvalidSort(resolver, parameter, request(null, "desc"));
        assertInvalidSort(resolver, parameter, request("secret", "sideways"));
        assertInvalidSort(resolver, parameter, request("missing", null));
        assertInvalidSort(resolver, parameter, request("secret", null));
    }

    private static void assertInvalidSort(
        FilteredQueryArgumentResolver resolver,
        MethodParameter parameter,
        MockHttpServletRequest request
    ) {
        assertThatThrownBy(() ->
            resolver.resolveArgument(parameter, null, new ServletWebRequest(request), null)
        ).isInstanceOf(FilterByException.class);
    }

    private static MockHttpServletRequest request(@Nullable String sortBy, @Nullable String sortOrder) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (sortBy != null) {
            request.setParameter("sortBy", sortBy);
        }
        if (sortOrder != null) {
            request.setParameter("sortOrder", sortOrder);
        }
        return request;
    }

    private static QueryFieldContextSource source(String operation, QueryFieldContext context, AtomicInteger calls) {
        return new QueryFieldContextSource() {
            @Override
            public boolean supports(String candidate) {
                return operation.equals(candidate);
            }

            @Override
            public QueryFieldContext resolve(String candidate, NativeWebRequest request) {
                calls.incrementAndGet();
                return context;
            }
        };
    }

    private static final class TestController {

        @QueryOperation("test.objects.list")
        void list(FilteredQuery<TestObject> filter) {}
    }

    private static final class TestObject {}
}
