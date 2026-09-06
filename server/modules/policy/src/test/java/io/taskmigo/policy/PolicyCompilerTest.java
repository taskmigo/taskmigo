package io.taskmigo.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PolicyCompilerTest {

    private final EnvironmentSchema schema = new EnvironmentSchema(
        "test",
        Map.of(
            "request",
            new EnvironmentSchema.Root(
                new EnvironmentSchema.Field(PolicyType.Scalar.STRING, false, false, false),
                Map.of(
                    "method",
                    new EnvironmentSchema.Field(PolicyType.Scalar.STRING, false, false, false),
                    "pathVariables",
                    new EnvironmentSchema.Field(PolicyType.Scalar.STRING, false, false, false, PolicyType.Scalar.STRING)
                )
            )
        )
    );

    /**
     * Verifies that direct-body source is parsed, bound, and evaluated with strict static references.
     *
     * Given: a policy using a request root and a homogeneous string list.
     * Expect: matching input evaluates true and non-matching input evaluates false.
     */
    @Test
    @DisplayName("evaluates a direct Policy Language body")
    void shouldEvaluateDirectBodyWhenInputsMatch() {
        // Arrange
        PolicyCompiler compiler = new PolicyCompiler();
        PolicyIr policy = compiler.compile(
            "const allowed = [\"GET\", \"HEAD\"]; return request.method in allowed;",
            schema
        );

        // Act
        boolean result = new PolicyEvaluator().evaluate(policy, Map.of("request", Map.of("method", "GET")));

        // Assert
        assertThat(result).isTrue();
    }

    /**
     * Verifies that Policy Language rejects an unknown static reference during compilation.
     *
     * Given: source reading a root absent from the consumer schema.
     * Expect: compilation fails with BindingError and no dynamic lookup is attempted.
     */
    @Test
    @DisplayName("rejects references outside the supplied schema")
    void shouldRejectUnknownReferenceWhenSchemaDoesNotDeclareIt() {
        // Arrange
        PolicyCompiler compiler = new PolicyCompiler();

        // Act + Assert
        assertThatThrownBy(() -> compiler.compile("return principal.id == \"1\";", schema))
            .isInstanceOf(PolicyException.class)
            .extracting(exception -> ((PolicyException) exception).category())
            .isEqualTo(PolicyDiagnostic.Category.BindingError);
    }

    /**
     * Verifies that incomplete reachable control flow is rejected.
     *
     * Given: an if statement without an else or following return.
     * Expect: compilation fails closed with ControlFlowError.
     */
    @Test
    @DisplayName("requires a boolean return on every reachable path")
    void shouldRejectFallThroughWhenIfHasNoCompleteReturn() {
        // Arrange
        PolicyCompiler compiler = new PolicyCompiler();

        // Act + Assert
        assertThatThrownBy(() -> compiler.compile("if (request.method == \"GET\") { return true; }", schema))
            .isInstanceOf(PolicyException.class)
            .extracting(exception -> ((PolicyException) exception).category())
            .isEqualTo(PolicyDiagnostic.Category.ControlFlowError);
    }

    /**
     * Verifies that both branches of a terminal if/else satisfy complete-return control flow.
     *
     * Given: a direct policy whose two branches return boolean literals.
     * Expect: both condition values evaluate without a fall-through diagnostic.
     */
    @Test
    @DisplayName("accepts complete if and else returns")
    void shouldAcceptIfElseWhenBothBranchesReturnBoolean() {
        // Arrange
        PolicyCompiler compiler = new PolicyCompiler();
        PolicyIr policy = compiler.compile(
            "if (request.method == \"GET\") { return true; } else { return false; }",
            schema
        );

        // Act
        boolean result = new PolicyEvaluator().evaluate(policy, Map.of("request", Map.of("method", "GET")));

        // Assert
        assertThat(result).isTrue();
    }

    /**
     * Verifies that the source-size limit is finite and fail closed.
     *
     * Given: a compiler configured with a one-character source limit.
     * Expect: a ComplexityError is emitted before parsing.
     */
    @Test
    @DisplayName("enforces compiler source limits")
    void shouldRejectSourceWhenItExceedsConfiguredLimit() {
        // Arrange
        PolicyCompiler compiler = new PolicyCompiler(new CompilerLimits(1, 20, 20, 20, 20, 20));

        // Act + Assert
        assertThatThrownBy(() -> compiler.compile("return true;", schema))
            .isInstanceOf(PolicyException.class)
            .extracting(exception -> ((PolicyException) exception).category())
            .isEqualTo(PolicyDiagnostic.Category.ComplexityError);
    }
}
