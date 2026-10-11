package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.foundation.TypeDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Enhancement237FilterByContractTest {

    private final QuerySchemaView schema = new QuerySchemaView() {
        private final QueryFieldDescriptor name = new QueryFieldDescriptor(
            QueryPath.of("name"),
            TypeDescriptor.of(String.class),
            false,
            Set.of(QueryOperator.EQ, QueryOperator.NE, QueryOperator.CONTAINS)
        );

        @Override
        public String operation() {
            return "test.customers.list";
        }

        @Override
        public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
            return List.of(this.name);
        }
    };

    @Test
    @DisplayName("should expose only enhancement 237 query operators")
    void queryOperatorSurfaceIsLimitedToEnhancement237() {
        assertThat(QueryOperator.values()).containsExactlyInAnyOrder(
            QueryOperator.AND,
            QueryOperator.OR,
            QueryOperator.EQ,
            QueryOperator.NE,
            QueryOperator.GT,
            QueryOperator.GTE,
            QueryOperator.LT,
            QueryOperator.LTE,
            QueryOperator.CONTAINS
        );
    }

    @Test
    @DisplayName("should compile contains for a declared string field")
    void filterBySupportsContainsFunctionForDeclaredStringField() {
        QueryPredicate<?> predicate = new FilterByCompiler().compile(this.schema, "contains(object.name, \"hon\")");

        assertThat(predicate.isAlwaysTrue()).isFalse();
        assertThat(predicate.isAlwaysFalse()).isFalse();
    }

    @Test
    @DisplayName("should expose only the object root to filterBy")
    void filterByExposesOnlyObjectRoot() {
        FilterByCompiler compiler = new FilterByCompiler();

        assertThatThrownBy(() -> compiler.compile(this.schema, "principal.id == \"p-1\"")).isInstanceOf(
            FilterByException.class
        );
        assertThatThrownBy(() -> compiler.compile(this.schema, "request.method == \"GET\"")).isInstanceOf(
            FilterByException.class
        );
        assertThatThrownBy(() -> compiler.compile(this.schema, "name == \"Phong\"")).isInstanceOf(
            FilterByException.class
        );
    }

    @Test
    @DisplayName("should reject filterBy syntax outside the approved surface")
    void filterByRejectsSyntaxOutsideApprovedSurface() {
        FilterByCompiler compiler = new FilterByCompiler();

        assertThatThrownBy(() -> compiler.compile(this.schema, "! (object.name == \"Phong\")")).isInstanceOf(
            FilterByException.class
        );
        assertThatThrownBy(() -> compiler.compile(this.schema, "object.name in [\"Phong\"]")).isInstanceOf(
            FilterByException.class
        );
        assertThatThrownBy(() -> compiler.compile(this.schema, "len(object.name) > 1")).isInstanceOf(
            FilterByException.class
        );
        assertThatThrownBy(() -> compiler.compile(this.schema, "object.name + \"x\" == \"Phongx\"")).isInstanceOf(
            FilterByException.class
        );
    }

    @Test
    @DisplayName("should expose operation scoped QuerySchemaView compilation")
    void filterByCompilerConsumesOperationScopedQuerySchemaView() {
        assertThat(Arrays.stream(FilterByCompiler.class.getMethods()))
            .filteredOn(method -> method.getName().equals("compile"))
            .anyMatch(this::isOperationScopedCompileMethod);
    }

    @Test
    @DisplayName("should not cache schema environments in FilterByCompiler")
    void filterByCompilerDoesNotCacheSchemaEnvironments() {
        assertThat(Arrays.stream(FilterByCompiler.class.getDeclaredFields()).map(Field::getType)).noneMatch(type ->
            Map.class.isAssignableFrom(type)
        );
    }

    private boolean isOperationScopedCompileMethod(Method method) {
        return Arrays.equals(method.getParameterTypes(), new Class<?>[] {
            QuerySchemaView.class,
            QueryFieldContext.class,
            String.class,
        });
    }
}
