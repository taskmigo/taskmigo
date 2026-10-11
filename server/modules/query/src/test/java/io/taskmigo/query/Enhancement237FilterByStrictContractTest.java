package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.foundation.TypeDescriptor;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Enhancement237FilterByStrictContractTest {

    private final FilterByCompiler compiler = new FilterByCompiler();

    @Test
    @DisplayName("should compare enum-backed fields with Taskmigo string literals")
    void enumBackedFieldUsesStringLanguageType() {
        QuerySchemaView schema = schema(field("status", TestStatus.class, false, QueryOperator.EQ));

        assertThatCode(() ->
            this.compiler.compile(schema, QueryFieldContext.empty(), "object.status == \"ACTIVE\"")
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should order temporal fields with Taskmigo ISO string literals")
    void temporalFieldUsesStringLanguageType() {
        QuerySchemaView schema = schema(field("retainedAt", Instant.class, true, QueryOperator.GTE));

        assertThatCode(() ->
            this.compiler.compile(schema, QueryFieldContext.empty(), "object.retainedAt >= \"2026-01-01T00:00:00Z\"")
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should reject object-field to object-field comparisons")
    void comparisonRequiresLiteralRightOperand() {
        QuerySchemaView schema = schema(
            field("name", String.class, false, QueryOperator.EQ),
            field("alias", String.class, false, QueryOperator.EQ)
        );

        assertThatThrownBy(() ->
            this.compiler.compile(schema, QueryFieldContext.empty(), "object.name == object.alias")
        ).isInstanceOf(FilterByException.class);
    }

    @Test
    @DisplayName("should reject reversed literal to object-field comparisons")
    void comparisonRequiresObjectFieldOnLeft() {
        QuerySchemaView schema = schema(field("name", String.class, false, QueryOperator.EQ));

        assertThatThrownBy(() ->
            this.compiler.compile(schema, QueryFieldContext.empty(), "\"alice\" == object.name")
        ).isInstanceOf(FilterByException.class);
    }

    @Test
    @DisplayName("should reject bare boolean object references")
    void booleanObjectFieldStillRequiresAnApprovedComparison() {
        QuerySchemaView schema = schema(field("active", Boolean.class, false, QueryOperator.EQ));

        assertThatThrownBy(() ->
            this.compiler.compile(schema, QueryFieldContext.empty(), "object.active")
        ).isInstanceOf(FilterByException.class);
    }

    @Test
    @DisplayName("should reject contains for non-string-like fields")
    void containsRequiresStringLikeFieldType() {
        QuerySchemaView schema = schema(field("id", UUID.class, false, QueryOperator.CONTAINS));

        assertThatThrownBy(() ->
            this.compiler.compile(schema, QueryFieldContext.empty(), "contains(object.id, \"abc\")")
        ).isInstanceOf(FilterByException.class);
    }

    @Test
    @DisplayName("should retain explicit OR composition over valid comparisons")
    void explicitOrCompositionStillWorks() {
        QuerySchemaView schema = schema(field("status", TestStatus.class, false, QueryOperator.EQ));

        assertThatCode(() ->
            this.compiler.compile(
                schema,
                QueryFieldContext.empty(),
                "object.status == \"ACTIVE\" || object.status == \"DISABLED\""
            )
        ).doesNotThrowAnyException();
    }

    private static QueryFieldDescriptor field(
        String path,
        Class<?> type,
        boolean nullable,
        QueryOperator... operators
    ) {
        return new QueryFieldDescriptor(QueryPath.of(path), TypeDescriptor.of(type), nullable, Set.of(operators));
    }

    private static QuerySchemaView schema(QueryFieldDescriptor... fields) {
        List<QueryFieldDescriptor> descriptors = List.of(fields);
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return "strict-filter-contract";
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return descriptors;
            }
        };
    }

    private enum TestStatus {
        ACTIVE,
        DISABLED,
    }
}
