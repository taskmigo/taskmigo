package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;

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
}
