package io.taskmigo.auth.authorization.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.statement.Scope;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaScriptPolicyEvaluatorTest {

    private final JavaScriptPolicyCompiler compiler = new JavaScriptPolicyCompiler();
    private final JavaScriptPolicyEvaluator evaluator = new JavaScriptPolicyEvaluator();

    /**
     * Verifies that the restricted policy subset evaluates logical, comparison, and numeric arithmetic semantics.
     *
     * Given: a compiled policy using const values, strict comparisons, and numeric arithmetic.
     * Expect: the evaluator returns true for matching request data and false for a non-matching path.
     */
    @Test
    @DisplayName("evaluates supported policy expressions")
    void shouldEvaluatePolicyWhenRequestValuesSatisfyExpressions() {
        // Arrange
        PolicyIr policy = this.compiler.compile(
            """
            export default ({ request }) => {
              const expected = 40 + 2;
              return request.count === expected && request.path === "/api/v0/users";
            };
            """,
            Scope.REQUEST
        );

        // Act
        boolean matching = this.evaluator.evaluate(
            policy,
            Map.of("request", Map.of("count", 42, "path", "/api/v0/users"))
        );
        boolean differentPath = this.evaluator.evaluate(
            policy,
            Map.of("request", Map.of("count", 42, "path", "/api/v1/users"))
        );

        // Assert
        assertThat(matching).isTrue();
        assertThat(differentPath).isFalse();
    }

    /**
     * Verifies that a policy result must be a JavaScript boolean.
     *
     * Given: a compiled policy IR containing a numeric expression.
     * Expect: evaluation fails closed with an authorization exception instead of coercing the number.
     */
    @Test
    @DisplayName("rejects non-boolean policy results")
    void shouldRejectPolicyResultWhenExpressionReturnsNumber() {
        // Arrange
        PolicyIr policy = new PolicyIr(new PolicyIr.Literal(1));

        // Act + Assert
        assertThatThrownBy(() -> this.evaluator.evaluate(policy, Map.of("request", Map.of("count", 1))))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("must return a boolean");
    }
}
