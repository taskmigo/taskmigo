package io.taskmigo.authorization.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.core.AuthorizationException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatementTargetPathMatcherTest {

    /**
     * Verifies the supported subset retains the route-regex features needed by Statement targets.
     *
     * Given: representative expressions using literals, classes, groups, alternation, anchors, and simple repetition.
     * Expect: every expression compiles through the bounded matcher contract.
     */
    @ParameterizedTest
    @ValueSource(
        strings = {
            "/api/v0/.*",
            "^/api/v0/(users|groups)/[0-9]+$",
            "/api/v0/files/[A-Za-z0-9_.-]+",
            "/api/v0/[^?]+",
            "/api/v0/items/\\d+",
        }
    )
    @DisplayName("accepts the supported target regex subset")
    void shouldCompileWhenExpressionUsesSupportedTargetSyntax(String expression) {
        assertThatCode(() -> StatementTargetPathMatcher.compile(expression)).doesNotThrowAnyException();
    }

    /**
     * Verifies engine-dependent or complexity-expanding syntax cannot reach RE2/J.
     *
     * Given: expressions using flags, non-capturing extensions, Unicode escapes, counted repetition, or backtracking.
     * Expect: each expression fails closed at the Taskmigo-owned contract boundary.
     */
    @ParameterizedTest
    @ValueSource(
        strings = {
            "(?i)/api/v0/users/[a-z]+",
            "(?:/api/v0/users|/api/v0/groups)",
            "/api/v0/users/\\p{Ll}+",
            "/api/v0/users/\\u0061",
            "/api/v0/users/\\x61",
            "/api/v0/users/a{1,2}",
            "^/(a+)\\1$",
            "^/api(?=/v0)",
        }
    )
    @DisplayName("rejects target regex syntax outside the supported subset")
    void shouldRejectWhenExpressionUsesUnsupportedTargetSyntax(String expression) {
        assertThatThrownBy(() -> StatementTargetPathMatcher.compile(expression))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("valid regular expression");
    }

    /**
     * Verifies expression structure cannot force unbounded parser recursion.
     *
     * Given: a syntactically balanced expression deeper than the Taskmigo grouping budget.
     * Expect: validation fails before the regex engine is invoked.
     */
    @Test
    @DisplayName("rejects excessive target regex nesting")
    void shouldRejectWhenExpressionNestingExceedsBudget() {
        String expression = "(".repeat(33) + "a" + ")".repeat(33);

        assertThatThrownBy(() -> StatementTargetPathMatcher.compile(expression))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("valid regular expression");
    }

    /**
     * Verifies known RE2/J compile-time hazards are rejected by the pre-engine validator.
     *
     * Given: the case-insensitive Unicode literal shape associated with RE2/J compile-time looping.
     * Expect: compilation fails closed inside the same bounded budget used for hot-path regression checks.
     */
    @Test
    @DisplayName("rejects known compile-time regex hazards within budget")
    void shouldRejectKnownCompileTimeHazardBeforeRegexEngineRuns() {
        String expression = "(?i)" + Character.toString(0x1c80);

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> StatementTargetPathMatcher.compile(expression)).isInstanceOf(
            AuthorizationException.class
        );
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(elapsed).isLessThan(Duration.ofMillis(250));
    }

    /**
     * Verifies non-ASCII expression literals stay outside the supported contract even without regex extensions.
     *
     * Given: a target expression containing a non-ASCII literal.
     * Expect: validation fails before RE2/J receives the expression.
     */
    @Test
    @DisplayName("rejects non-ascii target regex expressions")
    void shouldRejectWhenExpressionContainsNonAsciiLiteral() {
        String expression = "/api/v0/users/" + Character.toString(0x00e9);

        assertThatThrownBy(() -> StatementTargetPathMatcher.compile(expression))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("valid regular expression");
    }
}
