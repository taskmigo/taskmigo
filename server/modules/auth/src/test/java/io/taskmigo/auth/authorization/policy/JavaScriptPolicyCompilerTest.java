package io.taskmigo.auth.authorization.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.statement.Scope;
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
            .hasMessageContaining("object references");
        assertThat(this.compiler.compile(source, Scope.OBJECT)).isNotNull();
    }

    /**
     * Verifies that a request module can declare named resources and consume the resolved object root.
     *
     * Given: a resources export selecting a user by a request path variable and a policy reading that user.
     * Expect: both declarations compile into one immutable module with the named descriptor preserved.
     */
    @Test
    @DisplayName("compiles request resources and object references")
    void shouldCompileResourcesWhenRequestPolicySelectsNamedObjects() {
        // Arrange
        String source = """
        export function resources({ request, principal }) {
          return { user: resource("user", request.path.userId) };
        }
        export default ({ object }) => object.user.username === "alice";
        """;

        // Act
        JavaScriptPolicyModule module = this.compiler.compileModule(source, Scope.REQUEST);

        // Assert
        assertThat(module.resources())
            .singleElement()
            .satisfies(resource -> {
                assertThat(resource.name()).isEqualTo("user");
                assertThat(resource.type()).isEqualTo("user");
                assertThat(resource.key()).isEqualTo(
                    new PolicyIr.Reference("request", java.util.List.of("path", "userId"))
                );
            });
        assertThat(module.policy()).isNotNull();
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
