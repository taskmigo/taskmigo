package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

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
        QueryField name = new QueryField(
            QueryPath.of("name"),
            ResolvableType.forClass(String.class),
            false,
            Set.of(QueryOperator.NE, QueryOperator.EQ)
        );
        QueryField score = new QueryField(
            QueryPath.of("score"),
            ResolvableType.forClass(Integer.class),
            true,
            Set.of(QueryOperator.GE, QueryOperator.LT)
        );

        // Act
        QuerySchema<Contract> first = schema(List.of(name, score));
        QuerySchema<Contract> second = schema(List.of(score, name));

        // Assert
        assertThat(first.identity()).isEqualTo(second.identity());
    }

    private static QuerySchema<Contract> schema(Collection<QueryField> fields) {
        List<QueryField> declared = List.copyOf(fields);
        return new QuerySchema<>() {
            @Override
            public Class<Contract> queryType() {
                return Contract.class;
            }

            @Override
            public Optional<QueryField> field(QueryPath path) {
                return declared
                    .stream()
                    .filter(field -> field.path().equals(path))
                    .findFirst();
            }

            @Override
            public Collection<QueryField> fields() {
                return declared;
            }
        };
    }

    private static final class Contract {}
}
