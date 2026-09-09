package io.taskmigo.embeddedlanguage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
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
        assertThat(List.of(all, any, none)).containsExactly(true, false, true);
    }
}
