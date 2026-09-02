package io.taskmigo.auth.authorization.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.statement.ApiInfo;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.TargetInfo;
import io.taskmigo.auth.authorization.statement.TargetType;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorizationExpressionEvaluatorTest {

    private final AuthorizationCompiler compiler = new AuthorizationCompiler();
    private final AuthorizationExpressionEvaluator evaluator = new AuthorizationExpressionEvaluator();

    /**
     * Verifies that a compiled request condition can evaluate the values exposed by the request authorization layer.
     *
     * Given: a condition requiring the principal id and request method to match supplied values.
     * Expect: evaluation returns true for the matching principal and method and false for a different principal.
     */
    @Test
    @DisplayName("evaluates principal and request references")
    void shouldEvaluateReferencesWhenValuesMatchCondition() {
        // Arrange
        AuthorizationCompiler.Expression expression = this.compiler.compile(
            statement("principal.id == 'user-1' && request.method == 'GET'")
        );

        // Act
        boolean matching = this.evaluator.evaluate(expression, roots("user-1", "GET"));
        boolean differentPrincipal = this.evaluator.evaluate(expression, roots("user-2", "GET"));

        // Assert
        assertThat(matching).isTrue();
        assertThat(differentPrincipal).isFalse();
    }

    /**
     * Verifies that an expression result must be boolean before it can authorize a request.
     *
     * Given: a compiled numeric literal expression.
     * Expect: evaluation fails with an authorization validation error instead of coercing the number to true.
     */
    @Test
    @DisplayName("rejects non-boolean authorization results")
    void shouldRejectNonBooleanResultWhenExpressionReturnsNumber() {
        // Arrange
        AuthorizationCompiler.Expression expression = this.compiler.compile(statement("1 + 2"));

        // Act + Assert
        assertThatThrownBy(() -> this.evaluator.evaluate(expression, roots("user-1", "GET")))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("not boolean");
    }

    private static StatementInfo statement(String condition) {
        return new StatementInfo(
            java.util.UUID.randomUUID(),
            "test.statement",
            null,
            Effect.ALLOW,
            new TargetInfo(TargetType.REQUEST, new ApiInfo("GET", "/api/v0/test")),
            java.util.List.of(condition)
        );
    }

    private static Map<String, ?> roots(String userId, String method) {
        return Map.of("principal", Map.of("id", userId), "request", Map.of("method", method));
    }
}
