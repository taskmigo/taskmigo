package io.taskmigo.web.api.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.ListQuerySpec;
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
class ListQueryOptionsCompilerTest {

    @Mock
    private FilterByCompiler filters;

    @Mock
    private QuerySchemaView schema;

    @Mock
    private QueryPredicate<TestObject> predicate;

    /**
     * Verifies that the HTTP query model is compiled into the internal Query representation.
     *
     * Given: filterBy, descending sort syntax, an operation schema, and a server-owned runtime context.
     * Expect: the compiler returns the typed predicate, preserves the context, and resolves sorting against the schema.
     */
    @Test
    @DisplayName("compiles ListQueryOptions into the internal ListQuerySpec")
    void shouldCompileListQueryWhenRequestContainsWireParameters() {
        // Arrange
        QueryFieldContext context = new QueryFieldContext(Map.of("type", "incident"));
        QueryFieldDescriptor name = sortableField("name");
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.schema.field(QueryPath.of("name"), context)).thenReturn(Optional.of(name));
        doReturn(this.predicate).when(this.filters).compileTyped(this.schema, context, "object.name == \"alice\"");
        ListQueryOptionsCompiler compiler = new ListQueryOptionsCompiler(List.of(this.schema), this.filters);
        ListQueryOptions request = request("object.name == \"alice\"", List.of("-name"));

        // Act
        ListQuerySpec<TestObject> result = compiler.compile("test.objects.list", request, context);

        // Assert
        assertThat(result.predicate()).isSameAs(this.predicate);
        assertThat(result.context()).isEqualTo(context);
        assertThat(result.sorting()).containsExactly(new QuerySort(QueryPath.of("name"), QuerySort.Direction.DESC));
        verify(this.filters).compileTyped(this.schema, context, "object.name == \"alice\"");
    }

    /**
     * Verifies the default sort direction exposed by the HTTP contract.
     *
     * Given: a sort field without a direction prefix.
     * Expect: compilation requests ascending database-side ordering.
     */
    @Test
    @DisplayName("defaults sort direction to ascending when the prefix is absent")
    void shouldDefaultToAscendingWhenSortPrefixIsAbsent() {
        // Arrange
        QueryFieldContext context = QueryFieldContext.empty();
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.schema.field(QueryPath.of("name"), context)).thenReturn(Optional.of(sortableField("name")));
        doReturn(this.predicate).when(this.filters).compileTyped(this.schema, context, null);
        ListQueryOptionsCompiler compiler = new ListQueryOptionsCompiler(List.of(this.schema), this.filters);

        // Act
        ListQuerySpec<TestObject> result = compiler.compile(
            "test.objects.list",
            request(null, List.of("name")),
            context
        );

        // Assert
        assertThat(result.sorting()).containsExactly(new QuerySort(QueryPath.of("name"), QuerySort.Direction.ASC));
    }

    /**
     * Verifies that malformed, unknown, and non-sortable fields fail closed.
     *
     * Given: invalid public sort values.
     * Expect: each request fails with SortByException before executing the query.
     */
    @Test
    @DisplayName("rejects invalid sort values")
    void shouldRejectSortingWhenSortValueIsInvalid() {
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
        doReturn(Optional.empty()).when(this.schema).field(QueryPath.of("missing"), context);
        doReturn(Optional.of(secret)).when(this.schema).field(QueryPath.of("secret"), context);
        ListQueryOptionsCompiler compiler = new ListQueryOptionsCompiler(List.of(this.schema), this.filters);

        // Act / Assert
        assertInvalidSort(compiler, request(null, List.of("")), context);
        assertInvalidSort(compiler, request(null, List.of("-")), context);
        assertInvalidSort(compiler, request(null, List.of("+name")), context);
        assertInvalidSort(compiler, request(null, List.of("missing")), context);
        assertInvalidSort(compiler, request(null, List.of("secret")), context);
    }

    /**
     * Verifies that one logical field cannot be requested more than once with conflicting or duplicate directions.
     *
     * Given: the same sortable field appears twice in one ordered sort request.
     * Expect: compilation rejects the request instead of producing ambiguous ordering.
     */
    @Test
    @DisplayName("rejects duplicate sort fields")
    void shouldRejectSortingWhenFieldIsDuplicated() {
        // Arrange
        QueryFieldContext context = QueryFieldContext.empty();
        when(this.schema.operation()).thenReturn("test.objects.list");
        when(this.schema.field(QueryPath.of("name"), context)).thenReturn(Optional.of(sortableField("name")));
        ListQueryOptionsCompiler compiler = new ListQueryOptionsCompiler(List.of(this.schema), this.filters);

        // Act / Assert
        assertInvalidSort(compiler, request(null, List.of("name", "-name")), context);
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
        ListQueryOptionsCompiler compiler = new ListQueryOptionsCompiler(List.of(this.schema), this.filters);

        // Act / Assert
        assertThatThrownBy(() ->
            compiler.compile("test.objects.list", new ListQueryOptions(), QueryFieldContext.empty())
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("found 0");
    }

    private static void assertInvalidSort(
        ListQueryOptionsCompiler compiler,
        ListQueryOptions request,
        QueryFieldContext context
    ) {
        assertThatThrownBy(() -> compiler.compile("test.objects.list", request, context)).isInstanceOf(
            SortByException.class
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

    private static ListQueryOptions request(@Nullable String filterBy, List<String> sortBy) {
        ListQueryOptions request = new ListQueryOptions();
        request.setFilterBy(filterBy);
        request.setSortBy(sortBy);
        return request;
    }

    private static final class TestObject {}
}
