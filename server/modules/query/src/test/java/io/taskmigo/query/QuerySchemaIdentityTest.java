package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.foundation.TypeDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuerySchemaIdentityTest {

    /**
     * Verifies that Query Schema identity is independent of field and operator collection order.
     *
     * Given: equivalent schemas declared with reversed field order and operator sets.
     * Expect: both schemas produce the same canonical identity.
     */
    @Test
    @DisplayName("should canonicalize equivalent query schemas")
    void shouldProduceSameIdentityWhenSchemaCollectionOrderDiffers() {
        // Arrange
        QueryFieldDescriptor name = new QueryFieldDescriptor(
            QueryPath.of("name"),
            TypeDescriptor.of(String.class),
            false,
            Set.of(QueryOperator.NE, QueryOperator.EQ)
        );
        QueryFieldDescriptor score = new QueryFieldDescriptor(
            QueryPath.of("score"),
            TypeDescriptor.of(Integer.class),
            true,
            Set.of(QueryOperator.GTE, QueryOperator.LT)
        );

        // Act
        QuerySchemaView first = schema(List.of(name, score));
        QuerySchemaView second = schema(List.of(score, name));

        // Assert
        assertThat(first.identity()).isEqualTo(second.identity());
    }

    private static QuerySchemaView schema(Collection<QueryFieldDescriptor> fields) {
        List<QueryFieldDescriptor> declared = List.copyOf(fields);
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return "test.contract";
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return declared;
            }
        };
    }
}
