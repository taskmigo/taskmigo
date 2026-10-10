package io.taskmigo.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContainsLanguageTest {

    private final EnvironmentSchema schema = new EnvironmentSchema(
        "contains-test",
        Map.of(
            "text",
            new EnvironmentSchema.Root(new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false), Map.of())
        )
    );

    @Test
    @DisplayName("should evaluate contains using literal substring semantics")
    void shouldEvaluateContainsUsingLiteralSubstringSemantics() {
        CompiledSource source = new LanguageCompiler().compile(
            "contains(text, \"ali\")",
            this.schema,
            CompilationProfile.expression()
        );

        assertThat(source.evaluate(Map.of("text", "alice"))).isEqualTo(true);
        assertThat(source.evaluate(Map.of("text", "bob"))).isEqualTo(false);
    }

    @Test
    @DisplayName("should partially evaluate contains when its root becomes concrete")
    void shouldPartiallyEvaluateContainsWhenRootBecomesConcrete() {
        CompiledSource source = new LanguageCompiler().compile(
            "contains(text, \"ali\")",
            this.schema,
            CompilationProfile.expression()
        );

        assertThat(source.partialEvaluate(Map.of("text", "alice"))).isEqualTo(
            new PartialProgram.Concrete(true, LanguageType.Scalar.BOOL)
        );
    }

    @Test
    @DisplayName("should reject contains when its feature is disabled")
    void shouldRejectContainsWhenFeatureIsDisabled() {
        CompilationProfile profile = new CompilationProfile(
            CompilationMode.EXPRESSION,
            Set.of(CompilationFeature.LOGICAL_OPERATORS, CompilationFeature.EQUALITY_OPERATORS)
        );

        assertThatThrownBy(() -> new LanguageCompiler().compile("contains(text, \"ali\")", this.schema, profile))
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.FeatureError);
    }

    @Test
    @DisplayName("should reject contains when either operand is not a string")
    void shouldRejectContainsWhenOperandIsNotString() {
        assertThatThrownBy(() ->
            new LanguageCompiler().compile("contains(text, 1)", this.schema, CompilationProfile.expression())
        )
            .isInstanceOf(EmbeddedLanguageException.class)
            .extracting(exception -> ((EmbeddedLanguageException) exception).category())
            .isEqualTo(LanguageDiagnostic.Category.TypeError);
    }
}
