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

    @Test
    @DisplayName("compiles ListQueryOptions against an explicitly selected Query Contract")
    void shouldCompileListQueryWhenRequestContainsWireParameters() {
        QueryFieldContext context = new QueryFieldContext(Map.of("type", "incident"));
        QueryFieldDescriptor name = sortableField("name");
        when(this.schema.field(QueryPath.of("name"), context)).thenReturn(Optional.of(name));
        doReturn(this.predicate).when(this.filters).compileTyped(this.schema, context, "object.name == \"alice\"");
        ListQueryOptionsCompiler compiler = new ListQueryOptionsCompiler(this.filters);
        ListQueryOptions request = request("object.name == \"alice\"", List.of("-name"));

        ListQuerySpec<TestObject> result = compiler.compile(this.schema, request, context);

        assertThat(result.predicate()).isSameAs(this.predicate);
        assertThat(result.context()).isEqualTo(context);
        assertThat(result.sorting()).containsExactly(new QuerySort(QueryPath.of("name"), QuerySort.Direction.DESC));
        verify(this.filters).compileTyped(this.schema, context, "object.name == \"alice\"");
    }

    @Test
    @DisplayName("defaults sort direction to ascending when the prefix is absent")
    void shouldDefaultToAscendingWhenSortPrefixIsAbsent() {
        QueryFieldContext context = QueryFieldContext.empty();
        when(this.schema.field(QueryPath.of("name"), context)).thenReturn(Optional.of(sortableField("name")));
        doReturn(this.predicate).when(this.filters).compileTyped(this.schema, context, null);
        ListQueryOptionsCompiler compiler = new ListQueryOptionsCompiler(this.filters);

        ListQuerySpec<TestObject> result = compiler.compile(this.schema, request(null, List.of("name")), context);

        assertThat(result.sorting()).containsExactly(new QuerySort(QueryPath.of("name"), QuerySort.Direction.ASC));
    }

    @Test
    @DisplayName("rejects invalid sort values")
    void shouldRejectSortingWhenSortValueIsInvalid() {
        QueryFieldContext context = QueryFieldContext.empty();
        QueryFieldDescriptor secret = new QueryFieldDescriptor(
            QueryPath.of("secret"),
            TypeDescriptor.of(String.class),
            false,
            Set.of(QueryOperator.EQ),
            false
        );
        doReturn(Optional.empty()).when(this.schema).field(QueryPath.of("missing"), context);
        doReturn(Optional.of(secret)).when(this.schema).field(QueryPath.of("secret"), context);
        ListQueryOptionsCompiler compiler = new ListQueryOptionsCompiler(this.filters);

        assertInvalidSort(compiler, request(null, List.of("")), context);
        assertInvalidSort(compiler, request(null, List.of("-")), context);
        assertInvalidSort(compiler, request(null, List.of("+name")), context);
        assertInvalidSort(compiler, request(null, List.of("missing")), context);
        assertInvalidSort(compiler, request(null, List.of("secret")), context);
    }

    @Test
    @DisplayName("rejects duplicate sort fields")
    void shouldRejectSortingWhenFieldIsDuplicated() {
        QueryFieldContext context = QueryFieldContext.empty();
        when(this.schema.field(QueryPath.of("name"), context)).thenReturn(Optional.of(sortableField("name")));
        ListQueryOptionsCompiler compiler = new ListQueryOptionsCompiler(this.filters);

        assertInvalidSort(compiler, request(null, List.of("name", "-name")), context);
    }

    private void assertInvalidSort(
        ListQueryOptionsCompiler compiler,
        ListQueryOptions request,
        QueryFieldContext context
    ) {
        assertThatThrownBy(() -> compiler.compile(this.schema, request, context)).isInstanceOf(SortByException.class);
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
