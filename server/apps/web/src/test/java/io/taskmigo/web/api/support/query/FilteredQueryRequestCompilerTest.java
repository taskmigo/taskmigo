package io.taskmigo.web.api.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilterByException;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.QuerySchemaView;
import io.taskmigo.query.QuerySort;
import io.taskmigo.query.TypeDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FilteredQueryRequestCompilerTest {

    @Mock
    private FilterByCompiler filters;

    @Mock
    private QuerySchemaView schema;

    @Mock
    private QueryPredicate<TestObject> predicate;

    /**
     * Verifies that the HTTP query model is compiled into the internal Query representation.
     *
     * Given: filterBy, sortBy, sortOrder, an operation schema, and a server-owned runtime context.
     * Expect: the compiler returns the typed predicate, preserves the context, and resolves sorting against the schema.
     */
    @Test
    @DisplayName("compiles FilteredQueryRequest into the internal FilteredQuery")
    void shouldCompileFilteredQueryWhenRequestContainsWireParameters() {
        // Arrange
        QueryFieldContext context = new QueryFieldContext(Map.of("type", "incident"));
        QueryFieldDescriptor name = sortableField("name");
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.schema.field(QueryPath.of("name"), context)).thenReturn(Optional.of(name));
        doReturn(this.predicate).when(this.filters).compileTyped(this.schema, context, "object.name == \"alice\"");
        FilteredQueryRequestCompiler compiler = new FilteredQueryRequestCompiler(List.of(this.schema), this.filters);
        FilteredQueryRequest request = request("object.name == \"alice\"", "name", "desc");

        // Act
        FilteredQuery<TestObject> result = compiler.compile("test.objects.list", request, context);

        // Assert
        assertThat(result.predicate()).isSameAs(this.predicate);
        assertThat(result.context()).isEqualTo(context);
        assertThat(result.sorting()).containsExactly(new QuerySort(QueryPath.of("name"), QuerySort.Direction.DESC));
        verify(this.filters).compileTyped(this.schema, context, "object.name == \"alice\"");
    }

    /**
     * Verifies the default sort direction exposed by the HTTP contract.
     *
     * Given: sortBy is present while sortOrder is omitted.
     * Expect: compilation requests ascending database-side ordering.
     */
    @Test
    @DisplayName("defaults sortOrder to ascending when sortBy is present")
    void shouldDefaultToAscendingWhenSortOrderIsAbsent() {
        // Arrange
        QueryFieldContext context = QueryFieldContext.empty();
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.schema.field(QueryPath.of("name"), context)).thenReturn(Optional.of(sortableField("name")));
        doReturn(this.predicate).when(this.filters).compileTyped(this.schema, context, null);
        FilteredQueryRequestCompiler compiler = new FilteredQueryRequestCompiler(List.of(this.schema), this.filters);

        // Act
        FilteredQuery<TestObject> result = compiler.compile("test.objects.list", request(null, "name", null), context);

        // Assert
        assertThat(result.sorting()).containsExactly(new QuerySort(QueryPath.of("name"), QuerySort.Direction.ASC));
    }

    /**
     * Verifies that invalid HTTP sorting input keeps the existing resolver validation semantics.
     *
     * Given: incomplete, malformed, unknown, or non-sortable sorting requests.
     * Expect: each request fails with FilterByException before executing the query.
     */
    @Test
    @DisplayName("rejects invalid sorting wire parameters")
    void shouldRejectInvalidSortingWhenRequestIsInvalid() {
        // Arrange
        QueryFieldContext context = QueryFieldContext.empty();
        QueryFieldDescriptor secret = new QueryFieldDescriptor(
            QueryPath.of("secret"),
            TypeDescriptor.of(String.class),
            false,
            Set.of(QueryOperator.EQ),
            false
        );
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.schema.field(QueryPath.of("secret"), context)).thenReturn(Optional.of(secret));
        FilteredQueryRequestCompiler compiler = new FilteredQueryRequestCompiler(List.of(this.schema), this.filters);

        // Act / Assert
        assertInvalidSort(compiler, request(null, null, "desc"), context);
        assertInvalidSort(compiler, request(null, "secret", "sideways"), context);
        assertInvalidSort(compiler, request(null, "missing", null), context);
        assertInvalidSort(compiler, request(null, "secret", null), context);
    }

    /**
     * Verifies that compilation cannot silently use the wrong operation schema.
     *
     * Given: no Query Schema matches the requested operation.
     * Expect: compilation fails closed before parsing client input.
     */
    @Test
    @DisplayName("fails closed when the operation Query Schema is missing")
    void shouldRejectCompilationWhenOperationSchemaIsMissing() {
        // Arrange
        when(this.schema.operation()).thenReturn("test.objects.other");
        FilteredQueryRequestCompiler compiler = new FilteredQueryRequestCompiler(List.of(this.schema), this.filters);

        // Act / Assert
        assertThatThrownBy(() ->
            compiler.compile("test.objects.list", new FilteredQueryRequest(), QueryFieldContext.empty())
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("found 0");
    }

    private static void assertInvalidSort(
        FilteredQueryRequestCompiler compiler,
        FilteredQueryRequest request,
        QueryFieldContext context
    ) {
        assertThatThrownBy(() -> compiler.compile("test.objects.list", request, context)).isInstanceOf(
            FilterByException.class
        );
    }

    private static QueryFieldDescriptor sortableField(String path) {
        return new QueryFieldDescriptor(
            QueryPath.of(path),
            TypeDescriptor.of(String.class),
            false,
            Set.of(QueryOperator.EQ),
            true
        );
    }

    private static FilteredQueryRequest request(
        @Nullable String filterBy,
        @Nullable String sortBy,
        @Nullable String sortOrder
    ) {
        FilteredQueryRequest request = new FilteredQueryRequest();
        request.setFilterBy(filterBy);
        request.setSortBy(sortBy);
        request.setSortOrder(sortOrder);
        return request;
    }

    private static final class TestObject {}
}
