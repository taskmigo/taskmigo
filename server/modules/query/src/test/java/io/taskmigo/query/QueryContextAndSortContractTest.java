package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryContextAndSortContractTest {

    private static final QueryPredicate<TestObject> ALLOW_ALL = new QueryPredicate<>() {
        @Override
        public boolean isAlwaysTrue() {
            return true;
        }

        @Override
        public boolean isAlwaysFalse() {
            return false;
        }
    };

    @Test
    @DisplayName("query fields opt into sorting while the compatibility constructor remains non-sortable")
    void shouldExposeExplicitSortability() {
        QueryFieldDescriptor compatible = new QueryFieldDescriptor(
            QueryPath.of("name"),
            TypeDescriptor.of(String.class),
            false,
            Set.of(QueryOperator.EQ)
        );
        QueryFieldDescriptor sortable = new QueryFieldDescriptor(
            QueryPath.of("createdAt"),
            TypeDescriptor.of(String.class),
            false,
            Set.of(QueryOperator.EQ),
            true
        );

        assertThat(compatible.sortable()).isFalse();
        assertThat(sortable.sortable()).isTrue();
    }

    @Test
    @DisplayName("FilteredQuery carries one immutable runtime context and ordered sort request")
    void shouldCarryRuntimeContextAndSorting() {
        QueryFieldContext context = new QueryFieldContext(Map.of("scope", "incident"));
        QuerySort sort = new QuerySort(QueryPath.of("createdAt"), QuerySort.Direction.DESC);

        FilteredQuery<TestObject> query = new FilteredQuery<>(ALLOW_ALL, context, List.of(sort));

        assertThat(query.predicate()).isSameAs(ALLOW_ALL);
        assertThat(query.context()).isEqualTo(context);
        assertThat(query.sorting()).containsExactly(sort);
        assertThat(new FilteredQuery<>(ALLOW_ALL).context()).isEqualTo(QueryFieldContext.empty());
        assertThat(new FilteredQuery<>(ALLOW_ALL).sorting()).isEmpty();
    }

    @Test
    @DisplayName("schema identity includes effective sortability of the runtime field snapshot")
    void shouldIncludeSortabilityInSchemaIdentity() {
        QuerySchemaView schema = new QuerySchemaView() {
            @Override
            public String operation() {
                return "test.objects.list";
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                boolean sortable = Boolean.TRUE.equals(context.values().get("sortable"));
                return List.of(
                    new QueryFieldDescriptor(
                        QueryPath.of("severity"),
                        TypeDescriptor.of(String.class),
                        false,
                        Set.of(QueryOperator.EQ),
                        sortable
                    )
                );
            }
        };

        String unsortedIdentity = schema.identity(new QueryFieldContext(Map.of("sortable", false)));
        String sortedIdentity = schema.identity(new QueryFieldContext(Map.of("sortable", true)));

        assertThat(sortedIdentity).isNotEqualTo(unsortedIdentity);
    }

    private static final class TestObject {}
}
