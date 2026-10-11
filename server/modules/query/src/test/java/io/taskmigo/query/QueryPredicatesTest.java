package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.query.model.QueryExpression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryPredicatesTest {

    @Test
    @DisplayName("returns the composed predicate")
    void shouldReturnComposedPredicate() {
        QueryPredicates predicates = QueryPredicates.standard();
        QueryPredicate<Object> left = predicates.alwaysTrue();
        QueryPredicate<Object> right = predicates.alwaysFalse();

        QueryPredicate<Object> result = predicates.and(left, right);

        assertThat(result.isAlwaysFalse()).isTrue();
    }

    /**
     * Given: two typed predicates bound to different operation-schema identities.
     * Expect: composition rejects them before an expression can cross the schema boundary.
     */
    @Test
    @DisplayName("rejects composition across schema identities")
    void shouldRejectCompositionAcrossSchemaIdentities() {
        QueryPredicate<Object> left = QueryPredicateFactory.wrap("schema-a", new QueryExpression.Literal(true));
        QueryPredicate<Object> right = QueryPredicateFactory.wrap("schema-b", new QueryExpression.Literal(true));

        assertThatThrownBy(() -> QueryPredicates.standard().and(left, right))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("incompatible");
    }
}
