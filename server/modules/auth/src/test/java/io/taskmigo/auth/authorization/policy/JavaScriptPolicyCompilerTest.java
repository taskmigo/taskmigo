package io.taskmigo.auth.authorization.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.statement.Scope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaScriptPolicyCompilerTest {

    private final JavaScriptPolicyCompiler compiler = new JavaScriptPolicyCompiler();
    private final JavaScriptPolicyEvaluator evaluator = new JavaScriptPolicyEvaluator();

    /**
     * Verifies that a default-exported JavaScript arrow function is translated to parser-independent policy IR.
     *
     * Given: a request policy containing root destructuring, a const declaration, an if/else, and strict comparisons.
     * Expect: the compiled policy evaluates using supplied maps without executing JavaScript source.
     */
    @Test
    @DisplayName("compiles supported JavaScript request policy semantics")
    void shouldCompilePolicyWhenSupportedJavaScriptSemanticsAreUsed() {
        // Arrange
        String source = """
        export default ({ request, principal }) => {
          const expectedMethod = "GET";
          if (request.method === expectedMethod && request.path === "/api/v0/users") {
            return principal.enabled === true;
          } else {
            return false;
          }
        };
        """;

        // Act
        PolicyIr policy = this.compiler.compile(source, Scope.REQUEST);

        // Assert
        assertThat(this.evaluator.evaluate(policy, roots("GET", "/api/v0/users", true))).isTrue();
        assertThat(this.evaluator.evaluate(policy, roots("DELETE", "/api/v0/users", true))).isFalse();
    }

    /**
     * Verifies that policy compilation requires exactly the supported module entry point.
     *
     * Given: a source module without a default export and a source module whose default export is not a function.
     * Expect: both sources are rejected before they can be activated or evaluated.
     */
    @Test
    @DisplayName("rejects policies without a default-exported function")
    void shouldRejectPolicyWhenDefaultExportContractIsInvalid() {
        // Arrange
        String missingDefault = "export const policy = ({ request }) => request.method === 'GET';";
        String nonFunction = "export default true;";

        // Act + Assert
        assertThatThrownBy(() -> this.compiler.compile(missingDefault, Scope.REQUEST))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("default export");
        assertThatThrownBy(() -> this.compiler.compile(nonFunction, Scope.REQUEST))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("function");
    }

    /**
     * Verifies that compilation preserves values whose runtime type depends on authorization input.
     *
     * Given: policies returning no value, a string, null, or falling through an if statement.
     * Expect: each policy compiles, and runtime evaluation rejects its non-boolean result.
     */
    @Test
    @DisplayName("rejects policies with reachable non-boolean results")
    void shouldRejectPolicyWhenReachableResultIsNotBoolean() {
        // Arrange
        String noValue = "export default () => {};";
        String stringValue = "export default () => 'allow';";
        String nullValue = "export default () => null;";
        String fallThrough = "export default ({ principal }) => { if (principal.admin) { return true; } };";

        // Act + Assert
        assertNonBoolean(noValue);
        assertNonBoolean(stringValue);
        assertNonBoolean(nullValue);
        assertNonBoolean(fallThrough);
    }

    /**
     * Verifies that compile-time boolean expressions become direct constant policy IR.
     *
     * Given: a policy whose logical expression contains only constant operands.
     * Expect: the compiled expression is the boolean literal true.
     */
    @Test
    @DisplayName("folds constant boolean policy expressions")
    void shouldFoldConstantPolicyWhenExpressionHasNoAuthorizationReferences() {
        // Arrange
        String source = "export default () => true && (false || true);";

        // Act
        PolicyIr policy = this.compiler.compile(source, Scope.REQUEST);

        // Assert
        assertThat(policy.expression()).isEqualTo(new PolicyIr.Literal(true));
    }

    /**
     * Verifies that constant subexpressions are folded even when their surrounding policy reads an authorization
     * root.
     *
     * Given: a Request policy comparing a request field with a constant arithmetic expression.
     * Expect: the runtime reference remains in the Policy IR while the constant arithmetic expression is reduced to
     * one literal.
     */
    @Test
    @DisplayName("folds constant subexpressions inside reference-based policies")
    void shouldFoldConstantSubexpressionWhenPolicyContainsAuthorizationReference() {
        // Arrange
        String source = "export default ({ request }) => request.value === (40 + 2);";

        // Act
        PolicyIr.Binary expression = (PolicyIr.Binary) this.compiler.compile(source, Scope.REQUEST).expression();

        // Assert
        assertThat(expression.left()).isEqualTo(new PolicyIr.Reference("request", List.of("value")));
        assertThat(expression.right()).isEqualTo(new PolicyIr.Literal(42.0));
    }

    /**
     * Verifies that logical constant folding preserves JavaScript short-circuit behavior.
     *
     * Given: policies whose left logical operand is a constant false or true value and whose right operand reads a
     * request field.
     * Expect: false AND and true OR each reduce to the left constant without requiring the right operand.
     */
    @Test
    @DisplayName("preserves logical short circuit while folding constants")
    void shouldPreserveShortCircuitWhenLogicalOperandIsConstant() {
        // Arrange
        String andSource = "export default ({ request }) => false && request.missing;";
        String orSource = "export default ({ request }) => true || request.missing;";

        // Act
        PolicyIr andPolicy = this.compiler.compile(andSource, Scope.REQUEST);
        PolicyIr orPolicy = this.compiler.compile(orSource, Scope.REQUEST);

        // Assert
        assertThat(andPolicy.expression()).isEqualTo(new PolicyIr.Literal(false));
        assertThat(orPolicy.expression()).isEqualTo(new PolicyIr.Literal(true));
    }

    /**
     * Verifies that nested conditional branches continue into the enclosing statement sequence when they fall
     * through without returning.
     *
     * Given: a policy with an inner conditional return nested inside an outer conditional and a final fallback return.
     * Expect: the fallback is used when either condition is false, and the inner return is used only when both are
     * true.
     */
    @Test
    @DisplayName("preserves nested branch continuation semantics")
    void shouldUseFallbackWhenNestedBranchFallsThrough() {
        // Arrange
        String source = """
        export default ({ request }) => {
          if (request.first) {
            if (request.second) {
              return true;
            }
          }
          return false;
        };
        """;
        PolicyIr policy = this.compiler.compile(source, Scope.REQUEST);

        // Act
        boolean bothTrue = this.evaluator.evaluate(policy, Map.of("request", Map.of("first", true, "second", true)));
        boolean innerFalse = this.evaluator.evaluate(policy, Map.of("request", Map.of("first", true, "second", false)));
        boolean outerFalse = this.evaluator.evaluate(policy, Map.of("request", Map.of("first", false, "second", true)));

        // Assert
        assertThat(bothTrue).isTrue();
        assertThat(innerFalse).isFalse();
        assertThat(outerFalse).isFalse();
    }

    /**
     * Verifies that unsupported JavaScript calls cannot enter the policy IR.
     *
     * Given: a policy attempting to call an arbitrary method on the request root.
     * Expect: activation fails with a compiler diagnostic rather than evaluating host or JavaScript behavior.
     */
    @Test
    @DisplayName("rejects unsupported JavaScript operations")
    void shouldRejectPolicyWhenUnsupportedCallIsUsed() {
        // Arrange
        String source = "export default ({ request }) => request.method.toLowerCase('x') === 'get';";

        // Act + Assert
        assertThatThrownBy(() -> this.compiler.compile(source, Scope.REQUEST))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("unsupported policy expression");
    }

    /**
     * Verifies that syntax outside the SRS policy subset is rejected structurally.
     *
     * Given: policies using arrays, object literals, computed properties, ternaries, membership, or string helpers.
     * Expect: each policy is rejected by the compiler before authorization can execute it.
     */
    @Test
    @DisplayName("rejects policy syntax outside the authorization subset")
    void shouldRejectPolicyWhenUnsupportedSubsetSyntaxIsUsed() {
        // Arrange
        String array = "export default () => [true];";
        String object = "export default () => ({ allowed: true });";
        String computed = "export default ({ request }) => request['method'] === 'GET';";
        String ternary = "export default ({ request }) => request.method === 'GET' ? true : false;";
        String membership = "export default ({ request }) => request.method in request;";
        String helper = "export default ({ request }) => request.path.startsWith('/api');";

        // Act + Assert
        assertUnsupported(array);
        assertUnsupported(object);
        assertUnsupported(computed);
        assertUnsupported(ternary);
        assertUnsupported(membership);
        assertUnsupported(helper);
    }

    /**
     * Verifies that request policies cannot access an object that has not been explicitly resolved as a resource.
     *
     * Given: a request policy comparing a protected object's owner.
     * Expect: compilation fails for request scope while the object scope remains available for a later phase.
     */
    @Test
    @DisplayName("rejects object references in request policies")
    void shouldRejectPolicyWhenRequestReadsObjectRoot() {
        // Arrange
        String source = "export default ({ object }) => object.ownerId === 'owner-1';";

        // Act + Assert
        assertThatThrownBy(() -> this.compiler.compile(source, Scope.REQUEST))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("unsupported root");
        assertThat(this.compiler.compile(source, Scope.OBJECT)).isNotNull();
    }

    /**
     * Verifies that request authorization cannot declare or invoke business-resource loading.
     *
     * Given: policies containing a resources export or a resource intrinsic.
     * Expect: both policies are rejected before activation.
     */
    @Test
    @DisplayName("rejects request resource declarations and intrinsics")
    void shouldRejectPolicyWhenRequestDeclaresOrInvokesResources() {
        // Arrange
        String resources = """
        export function resources({ request, principal }) {
          return { user: resource("user", request.pathVariables.userId) };
        }
        export default () => true;
        """;
        String intrinsic = "export default ({ request }) => resource('user', request.pathVariables.userId);";

        // Act + Assert
        assertUnsupported(resources);
        assertUnsupported(intrinsic);
        assertThatThrownBy(() -> this.compiler.compile(resources, Scope.OBJECT)).isInstanceOf(
            AuthorizationException.class
        );
    }

    /**
     * Verifies that modulo arithmetic is represented in Policy IR for Request authorization.
     *
     * Given: a Request policy checking whether a numeric request value is even.
     * Expect: the policy evaluates true for an even value and false for an odd value.
     */
    @Test
    @DisplayName("evaluates modulo arithmetic in request policies")
    void shouldEvaluateModuloWhenRequestPolicyUsesNumericArithmetic() {
        // Arrange
        PolicyIr policy = this.compiler.compile(
            "export default ({ request }) => request.value % 2 === 0;",
            Scope.REQUEST
        );

        // Act
        boolean even = this.evaluator.evaluate(policy, Map.of("request", Map.of("value", 4)));
        boolean odd = this.evaluator.evaluate(policy, Map.of("request", Map.of("value", 5)));

        // Assert
        assertThat(even).isTrue();
        assertThat(odd).isFalse();
    }

    private static Map<String, ?> roots(String method, String path, boolean enabled) {
        return Map.of("request", Map.of("method", method, "path", path), "principal", Map.of("enabled", enabled));
    }

    private void assertNonBoolean(String source) {
        PolicyIr policy = this.compiler.compile(source, Scope.REQUEST);
        assertThatThrownBy(() -> this.evaluator.evaluate(policy, Map.of("principal", Map.of("admin", false))))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("boolean");
    }

    private void assertUnsupported(String source) {
        assertThatThrownBy(() -> this.compiler.compile(source, Scope.REQUEST)).isInstanceOf(
            AuthorizationException.class
        );
    }
}
