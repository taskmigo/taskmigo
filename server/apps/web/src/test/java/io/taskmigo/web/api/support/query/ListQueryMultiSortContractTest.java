package io.taskmigo.web.api.support.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListQueryMultiSortContractTest {

    @Mock
    private FilterByCompiler filters;

    @Mock
    private QuerySchemaView schema;

    @Mock
    private QueryPredicate<TestObject> predicate;

    @Test
    @DisplayName("compiles repeated sortBy values in priority order")
    void shouldCompileMultipleSortFieldsWhenRequestContainsRepeatedSortByValues() {
        QueryFieldContext context = QueryFieldContext.empty();
        when(this.schema.field(QueryPath.of("name"), context)).thenReturn(
            Optional.of(sortableField("name", String.class))
        );
        when(this.schema.field(QueryPath.of("createdAt"), context)).thenReturn(
            Optional.of(sortableField("createdAt", Instant.class))
        );
        doReturn(this.predicate).when(this.filters).compileTyped(this.schema, context, null);
        ListQueryOptions request = new ListQueryOptions();
        request.setSortBy(List.of("name", "-createdAt"));
        ListQueryOptionsCompiler compiler = new ListQueryOptionsCompiler(this.filters);

        ListQuerySpec<TestObject> result = compiler.compile(this.schema, request, context);

        assertThat(result.sorting()).containsExactly(
            new QuerySort(QueryPath.of("name"), QuerySort.Direction.ASC),
            new QuerySort(QueryPath.of("createdAt"), QuerySort.Direction.DESC)
        );
    }

    private static QueryFieldDescriptor sortableField(String path, Class<?> type) {
        return new QueryFieldDescriptor(
            QueryPath.of(path),
            TypeDescriptor.of(type),
            false,
            Set.of(QueryOperator.EQ),
            true
        );
    }

    private static final class TestObject {}
}
