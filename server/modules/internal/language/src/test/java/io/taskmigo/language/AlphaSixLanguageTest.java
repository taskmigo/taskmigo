package io.taskmigo.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlphaSixLanguageTest {

    private final EnvironmentSchema schema = new EnvironmentSchema(
        "alpha6",
        Map.of(
            "record",
            new EnvironmentSchema.Root(
                new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, true),
                Map.of(
                    "emails",
                    new EnvironmentSchema.Field(new LanguageType.ListType(LanguageType.Scalar.STRING), false, true)
                )
            )
        )
    );

    /**
     * Verifies that EXPRESSION mode compiles a source expression without a return wrapper.
     *
     * Given: the source `1 + 2` and an expression profile.
     * Expect: the compiler produces a Number expression whose direct evaluation is 3.
     */
    @Test
    @DisplayName("should evaluate an expression source when expression mode is selected")
    void shouldEvaluateExpressionWhenExpressionModeIsSelected() {
        // Arrange
        CompilationProfile profile = CompilationProfile.expression();

        // Act
        SemanticAst program = new EmbeddedLanguageCompiler().compile("1 + 2", schema, profile);

        // Assert
        assertThat(new EmbeddedLanguageEvaluator().evaluate(program, Map.of())).isEqualTo(new BigDecimal("3"));
    }

    /**
     * Verifies that the public compiled-source facade owns direct execution without exposing evaluator lifecycle.
     *
     * Given: an expression compiled through LanguageCompiler.
     * Expect: the returned CompiledSource reports its Number type and evaluates to 3.
     */
    @Test
    @DisplayName("should evaluate a compiled source through the public language facade")
    void shouldEvaluateCompiledSourceWhenUsingLanguageCompiler() {
        // Arrange
        LanguageCompiler compiler = new LanguageCompiler();

        // Act
        CompiledSource source = compiler.compile("1 + 2", this.schema, CompilationProfile.expression());

        // Assert
        assertThat(source.resultType()).isEqualTo(LanguageType.Scalar.NUMBER);
        assertThat(source.evaluate(Map.of())).isEqualTo(new BigDecimal("3"));
    }

    /**
     * Verifies that optimized runtime-number handling preserves the prior decimal interpretation of Float values.
     *
     * Given: a schema Number root supplied as Float value 0.1 and source comparing it with decimal literal 0.1.
     * Expect: direct evaluation returns true exactly as the previous Number-to-decimal conversion did.
     */
    @Test
    @DisplayName("should preserve float decimal semantics when evaluating numeric input")
    void shouldPreserveFloatDecimalSemanticsWhenEvaluatingNumericInput() {
        // Arrange
        EnvironmentSchema numericSchema = new EnvironmentSchema(
            "numeric",
            Map.of(
                "number",
                new EnvironmentSchema.Root(
                    new EnvironmentSchema.Field(LanguageType.Scalar.NUMBER, false, false),
                    Map.of()
                )
            )
        );
        CompiledSource source = new LanguageCompiler().compile(
            "number == 0.1",
            numericSchema,
            CompilationProfile.expression()
        );

        // Act
        Object result = source.evaluate(Map.of("number", 0.1F));

        // Assert
        assertThat(result).isEqualTo(true);
    }

    /**
     * Verifies that a disabled language feature is rejected before an executable artifact is produced.
     *
     * Given: an expression using list literals and a profile with list literals disabled.
     * Expect: compilation fails with FeatureError.
     */
    @Test
    @DisplayName("should reject a disabled feature when the source uses it")
    void shouldRejectDisabledFeatureWhenSourceUsesListLiteral() {
        // Arrange
        CompilationProfile profile = new CompilationProfile(
            CompilationMode.EXPRESSION,
            EnumSet.complementOf(EnumSet.of(CompilationFeature.LIST_LITERALS))
        );

        // Act + Assert
        assertThatThrownBy(() -> new EmbeddedLanguageCompiler().compile("[1]", schema, profile))
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.FeatureError);
    }

    /**
     * Verifies that collection quantifiers use the specified empty-list identities.
     *
     * Given: all, any, and none over a concrete empty list.
     * Expect: results are true, false, and true respectively.
     */
    @Test
    @DisplayName("should apply empty collection quantifier identities")
    void shouldApplyEmptyCollectionQuantifierIdentitiesWhenCollectionIsEmpty() {
        // Arrange
        EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();
        EmbeddedLanguageEvaluator evaluator = new EmbeddedLanguageEvaluator();

        // Act
        Object all = evaluator.evaluate(compiler.compile("return all([], item => true);", schema), Map.of());
        Object any = evaluator.evaluate(compiler.compile("return any([], item => true);", schema), Map.of());
        Object none = evaluator.evaluate(compiler.compile("return none([], item => true);", schema), Map.of());

        // Assert
        assertThat(all).isEqualTo(true);
        assertThat(any).isEqualTo(false);
        assertThat(none).isEqualTo(true);
    }

    /**
     * Verifies that nested quantifiers can capture an outer restricted-lambda binding.
     *
     * Given: an outer `all` whose inner `any` compares each inner element with the current outer element.
     * Expect: direct evaluation resolves the lexical capture and returns true without replacing the outer binding.
     */
    @Test
    @DisplayName("should preserve outer lambda bindings when quantifiers are nested")
    void shouldPreserveOuterLambdaBindingWhenQuantifiersAreNested() {
        // Arrange
        CompiledSource source = new LanguageCompiler().compile(
            "all([1, 2], outer => any([1, 2], inner => inner == outer))",
            this.schema,
            CompilationProfile.expression()
        );

        // Act
        Object result = source.evaluate(Map.of());

        // Assert
        assertThat(result).isEqualTo(true);
    }

    /**
     * Verifies that root-independent quantified expressions are concrete during partial evaluation.
     *
     * Given: a nested quantified expression whose values are entirely source literals.
     * Expect: partial evaluation resolves the lambda bindings and returns a concrete true result.
     */
    @Test
    @DisplayName("should fully specialize root independent quantified expressions")
    void shouldFullySpecializeQuantifiedExpressionWhenNoRootsAreRequired() {
        // Arrange
        CompiledSource source = new LanguageCompiler().compile(
            "all([1, 2], outer => any([1, 2], inner => inner == outer))",
            this.schema,
            CompilationProfile.expression()
        );

        // Act
        PartialProgram result = source.partialEvaluate(Map.of());

        // Assert
        assertThat(result).isEqualTo(new PartialProgram.Concrete(true, LanguageType.Scalar.BOOL));
    }
}
