package io.taskmigo.jpaquery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JpaQueryExpressionBinderTest {

    @Test
    @DisplayName("escapes literal SQL LIKE metacharacters exactly once for contains")
    void shouldEscapeLiteralLikeMetacharactersWhenBuildingContainsPattern() {
        assertThat(JpaQueryExpressionBinder.literalContainsPattern("a%b_c\\d")).isEqualTo("%a\\%b\\_c\\\\d%");
    }
}
