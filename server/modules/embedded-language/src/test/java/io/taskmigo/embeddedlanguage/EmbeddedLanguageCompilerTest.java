package io.taskmigo.embeddedlanguage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmbeddedLanguageCompilerTest {

    private final EnvironmentSchema schema = new EnvironmentSchema(
        "test",
        Map.of(
            "request",
            new EnvironmentSchema.Root(
                new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false),
                Map.of(
                    "method",
                    new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false),
                    "optional",
                    new EnvironmentSchema.Field(LanguageType.Scalar.STRING, true, false),
                    "pathVariables",
                    new EnvironmentSchema.Field(
                        LanguageType.Scalar.STRING,
                        false,
                        false,
                        LanguageType.Scalar.STRING
                    )
                )
            )
        )
    );

    @Test
    @DisplayName("evaluates a direct Embedded Language body")
    void shouldEvaluateDirectBodyWhenInputsMatch() {
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();
        SemanticAst program = compiler.compile(
            "const allowed = [\"GET\", \"HEAD\"]; return request.method in allowed;",
            schema
        );

        Object result = new EmbeddedLanguageEvaluator().evaluate(program, Map.of("request", Map.of("method", "GET")));

        assertThat(result).isEqualTo(true);
        assertThat(program.resultType()).isEqualTo(LanguageType.Scalar.BOOL);
    }

    @Test
    @DisplayName("supports non-boolean program result types")
    void shouldEvaluateStringNumberAndListProgramResults() {
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();
        EmbeddedLanguageEvaluator evaluator = new EmbeddedLanguageEvaluator();

        SemanticAst stringProgram = compiler.compile("return request.method;", schema);
        SemanticAst numberProgram = compiler.compile("return 42;", schema);
        SemanticAst listProgram = compiler.compile("return [\"GET\", \"HEAD\"];", schema);

        assertThat(stringProgram.resultType()).isEqualTo(LanguageType.Scalar.STRING);
        assertThat(evaluator.evaluate(stringProgram, Map.of("request", Map.of("method", "GET")))).isEqualTo("GET");
        assertThat(numberProgram.resultType()).isEqualTo(LanguageType.Scalar.NUMBER);
        assertThat(evaluator.evaluate(numberProgram, Map.of())).isEqualTo(new BigDecimal("42"));
        assertThat(listProgram.resultType()).isEqualTo(new LanguageType.ListType(LanguageType.Scalar.STRING));
        assertThat(evaluator.evaluate(listProgram, Map.of())).isEqualTo(List.of("GET", "HEAD"));
    }

    @Test
    @DisplayName("supports nullable program results")
    void shouldEvaluateNullableProgramResult() {
        SemanticAst program = new EmbeddedLanguageCompiler().compile("return request.optional;", schema);
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("optional", null);

        Object result = new EmbeddedLanguageEvaluator().evaluate(program, Map.of("request", request));

        assertThat(program.resultType()).isEqualTo(LanguageType.Scalar.STRING);
        assertThat(program.resultNullable()).isTrue();
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("rejects references outside the supplied schema")
    void shouldRejectUnknownReferenceWhenSchemaDoesNotDeclareIt() {
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();

        assertThatThrownBy(() -> compiler.compile("return principal.id == \"1\";", schema))
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.BindingError);
    }

    @Test
    @DisplayName("requires a return on every reachable path")
    void shouldRejectFallThroughWhenIfHasNoCompleteReturn() {
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();

        assertThatThrownBy(() -> compiler.compile("if (request.method == \"GET\") { return true; }", schema))
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.ControlFlowError);
    }

    @Test
    @DisplayName("accepts complete if and else returns")
    void shouldAcceptIfElseWhenBothBranchesReturnValues() {
        SemanticAst program = new EmbeddedLanguageCompiler().compile(
            "if (request.method == \"GET\") { return \"yes\"; } else { return \"no\"; }",
            schema
        );

        Object result = new EmbeddedLanguageEvaluator().evaluate(program, Map.of("request", Map.of("method", "GET")));

        assertThat(result).isEqualTo("yes");
        assertThat(program.resultType()).isEqualTo(LanguageType.Scalar.STRING);
    }

    @Test
    @DisplayName("rejects conditionals with mismatched branch types")
    void shouldRejectIfElseWhenBranchTypesDiffer() {
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();

        assertThatThrownBy(() ->
            compiler.compile("if (request.method == \"GET\") { return true; } else { return \"no\"; }", schema)
        )
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.TypeError);
    }

    @Test
    @DisplayName("allows a null branch with a compatible result type")
    void shouldAllowNullableCompatibleConditionalResult() {
        SemanticAst program = new EmbeddedLanguageCompiler().compile(
            "if (request.method == \"GET\") { return null; } else { return \"other\"; }",
            schema
        );

        assertThat(program.resultType()).isEqualTo(LanguageType.Scalar.STRING);
        assertThat(program.resultNullable()).isTrue();
    }

    @Test
    @DisplayName("allows shadowing in a nested lexical block")
    void shouldResolveNestedBindingInItsOwnLexicalScope() {
        SemanticAst program = new EmbeddedLanguageCompiler().compile(
            "const method = \"GET\"; if (request.method == \"POST\") { const method = \"POST\"; return method == request.method; } return method == request.method;",
            schema
        );

        Object result = new EmbeddedLanguageEvaluator().evaluate(program, Map.of("request", Map.of("method", "GET")));

        assertThat(result).isEqualTo(true);
    }

    @Test
    @DisplayName("rejects an incompatible partial-evaluation input")
    void shouldRejectPartialInputWhenRuntimeTypeDiffersFromSchema() {
        SemanticAst program = new EmbeddedLanguageCompiler().compile("return request.method == \"GET\";", schema);

        assertThatThrownBy(() ->
            new EmbeddedLanguagePartialEvaluator().partial(program, Map.of("request", Map.of("method", 7)))
        )
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.TypeError);
    }

    @Test
    @DisplayName("returns generic concrete and residual partial results")
    void shouldPartialEvaluateNonBooleanPrograms() {
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();
        PartialProgram concrete = new EmbeddedLanguagePartialEvaluator().partial(
            compiler.compile("return 42;", schema),
            Map.of()
        );
        EnvironmentSchema symbolic = new EnvironmentSchema(
            "symbolic",
            Map.of(
                "record",
                new EnvironmentSchema.Root(
                    new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, true),
                    Map.of("score", new EnvironmentSchema.Field(LanguageType.Scalar.NUMBER, false, true))
                )
            )
        );
        PartialProgram residual = new EmbeddedLanguagePartialEvaluator().partial(
            compiler.compile("return record.score + 1;", symbolic),
            Map.of()
        );

        assertThat(concrete).isEqualTo(new PartialProgram.Concrete(new BigDecimal("42"), LanguageType.Scalar.NUMBER));
        assertThat(residual).isInstanceOf(PartialProgram.Residual.class);
        assertThat(residual.type()).isEqualTo(LanguageType.Scalar.NUMBER);
    }

    @Test
    @DisplayName("rejects unknown values that may not remain symbolic")
    void shouldRejectNonSymbolicUnknownDuringPartialEvaluation() {
        SemanticAst program = new EmbeddedLanguageCompiler().compile("return request.method;", schema);

        assertThatThrownBy(() -> new EmbeddedLanguagePartialEvaluator().partial(program, Map.of()))
            .isInstanceOf(EmbeddedLanguageException.class)
            .hasMessageContaining("may not remain symbolic");
    }

    @Test
    @DisplayName("enforces compiler source limits")
    void shouldRejectSourceWhenItExceedsConfiguredLimit() {
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler(new CompilerLimits(1, 20, 20, 20, 20, 20));

        assertThatThrownBy(() -> compiler.compile("return true;", schema))
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.ComplexityError);
    }
}
