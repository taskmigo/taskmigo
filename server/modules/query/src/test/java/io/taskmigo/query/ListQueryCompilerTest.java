package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListQueryCompilerTest {

    private static final QueryFieldDescriptor NAME = new QueryFieldDescriptor(
        QueryPath.of("name"),
        TypeDescriptor.of(String.class),
        false,
        Set.of(QueryOperator.EQ),
        true
    );

    private static final QueryFieldDescriptor SECRET = new QueryFieldDescriptor(
        QueryPath.of("secret"),
        TypeDescriptor.of(String.class),
        false,
        Set.of(QueryOperator.EQ),
        false
    );

    private final QuerySchemaView schema = new QuerySchemaView() {
        @Override
        public String operation() {
            return "test.objects.list";
        }

        @Override
        public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
            return List.of(NAME, SECRET);
        }
    };

    @Test
    @DisplayName("compiles transport-neutral list intent against an explicit feature schema")
    void shouldCompileListIntentAgainstExplicitSchema() {
        QueryFieldContext context = QueryFieldContext.empty();

        ListQuerySpec<TestObject> result = new ListQueryCompiler().compile(
            this.schema,
            context,
            new ListQueryRequest(null, List.of("-name"))
        );

        assertThat(result.context()).isSameAs(context);
        assertThat(result.sorting()).containsExactly(new QuerySort(QueryPath.of("name"), QuerySort.Direction.DESC));
    }

    @Test
    @DisplayName("rejects unknown, non-sortable, duplicate and plus-prefixed sort fields")
    void shouldRejectInvalidSortIntent() {
        ListQueryCompiler compiler = new ListQueryCompiler();
        QueryFieldContext context = QueryFieldContext.empty();

        assertThatThrownBy(() -> compiler.compile(this.schema, context, request("missing"))).isInstanceOf(
            SortByException.class
        );
        assertThatThrownBy(() -> compiler.compile(this.schema, context, request("secret"))).isInstanceOf(
            SortByException.class
        );
        assertThatThrownBy(() ->
            compiler.compile(this.schema, context, new ListQueryRequest(null, List.of("name", "-name")))
        ).isInstanceOf(SortByException.class);
        assertThatThrownBy(() -> compiler.compile(this.schema, context, request("+name"))).isInstanceOf(
            SortByException.class
        );
    }

    private static ListQueryRequest request(String sort) {
        return new ListQueryRequest(null, List.of(sort));
    }

    private static final class TestObject {}
}
