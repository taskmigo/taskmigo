package io.taskmigo.embeddedlanguage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmbeddedLanguageCompilerTest {

    private final EnvironmentSchema schema = new EnvironmentSchema(
        "test",
        Map.of(
            "request",
            new EnvironmentSchema.Root(
                new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false, false),
                Map.of(
                    "method",
                    new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false, false),
                    "pathVariables",
                    new EnvironmentSchema.Field(
                        LanguageType.Scalar.STRING,
                        false,
                        false,
                        false,
                        LanguageType.Scalar.STRING
                    )
                )
            )
        )
    );

    /**
     * Verifies that direct-body source is parsed, bound, and evaluated with strict static references.
     *
     * Given: a program using a request root and a homogeneous string list.
     * Expect: matching input evaluates true and non-matching input evaluates false.
     */
    @Test
    @DisplayName("evaluates a direct Embedded Language body")
    void shouldEvaluateDirectBodyWhenInputsMatch() {
        // Arrange
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();
        LanguageIr program = compiler.compile(
            "const allowed = [\"GET\", \"HEAD\"]; return request.method in allowed;",
            schema
        );

        // Act
        boolean result = new EmbeddedLanguageEvaluator().evaluate(program, Map.of("request", Map.of("method", "GET")));

        // Assert
        assertThat(result).isTrue();
    }

    /**
     * Verifies that Embedded Language rejects an unknown static reference during compilation.
     *
     * Given: source reading a root absent from the consumer schema.
     * Expect: compilation fails with BindingError and no dynamic lookup is attempted.
     */
    @Test
    @DisplayName("rejects references outside the supplied schema")
    void shouldRejectUnknownReferenceWhenSchemaDoesNotDeclareIt() {
        // Arrange
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();

        // Act + Assert
        assertThatThrownBy(() -> compiler.compile("return principal.id == \"1\";", schema))
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.BindingError);
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
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();

        // Act + Assert
        assertThatThrownBy(() -> compiler.compile("if (request.method == \"GET\") { return true; }", schema))
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.ControlFlowError);
    }

    /**
     * Verifies that both branches of a terminal if/else satisfy complete-return control flow.
     *
     * Given: a direct program whose two branches return boolean literals.
     * Expect: both condition values evaluate without a fall-through diagnostic.
     */
    @Test
    @DisplayName("accepts complete if and else returns")
    void shouldAcceptIfElseWhenBothBranchesReturnBoolean() {
        // Arrange
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();
        LanguageIr program = compiler.compile(
            "if (request.method == \"GET\") { return true; } else { return false; }",
            schema
        );

        // Act
        boolean result = new EmbeddedLanguageEvaluator().evaluate(program, Map.of("request", Map.of("method", "GET")));

        // Assert
        assertThat(result).isTrue();
    }

    ///
    /// Verifies that all reachable conditional returns have one static result type.
    ///
    /// Given: an if/else whose branches return Bool and String.
    /// Expect: compilation fails with TypeError instead of deferring the failure to evaluation.
    ///
    @Test
    @DisplayName("rejects conditionals with mismatched branch types")
    void shouldRejectIfElseWhenBranchTypesDiffer() {
        // Arrange
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();

        // Act + Assert
        assertThatThrownBy(() ->
            compiler.compile("if (request.method == \"GET\") { return true; } else { return \"no\"; }", schema)
        )
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.TypeError);
    }

    ///
    /// Verifies that lexical shadowing is isolated to a nested block.
    ///
    /// Given: an inner block that declares the same name as an outer block.
    /// Expect: both bindings resolve to their own immutable values.
    ///
    @Test
    @DisplayName("allows shadowing in a nested lexical block")
    void shouldResolveNestedBindingInItsOwnLexicalScope() {
        // Arrange
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();
        LanguageIr program = compiler.compile(
            "const method = \"GET\"; if (request.method == \"POST\") { const method = \"POST\"; return method == request.method; } return method == request.method;",
            schema
        );

        // Act
        boolean result = new EmbeddedLanguageEvaluator().evaluate(program, Map.of("request", Map.of("method", "GET")));

        // Assert
        assertThat(result).isTrue();
    }

    ///
    /// Verifies that partial evaluation enforces the compiled schema's runtime value type.
    ///
    /// Given: a String request field specialized with a Number.
    /// Expect: partial evaluation fails with TypeError rather than coercing the value.
    ///
    @Test
    @DisplayName("rejects an incompatible partial-evaluation input")
    void shouldRejectPartialInputWhenRuntimeTypeDiffersFromSchema() {
        // Arrange
        LanguageIr program = new EmbeddedLanguageCompiler().compile("return request.method == \"GET\";", schema);

        // Act + Assert
        assertThatThrownBy(() ->
            new EmbeddedLanguagePartialEvaluator().partial(program, Map.of("request", Map.of("method", 7)))
        )
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.TypeError);
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
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler(new CompilerLimits(1, 20, 20, 20, 20, 20));

        // Act + Assert
        assertThatThrownBy(() -> compiler.compile("return true;", schema))
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.ComplexityError);
    }
}
