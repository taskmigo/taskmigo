package io.taskmigo.authorization.embeddedlanguage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.language.CompilationFeature;
import io.taskmigo.language.CompilationMode;
import io.taskmigo.language.CompilationProfile;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.EnvironmentSchema;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageType;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorizationCompilationProfileTest {

    private static final EnvironmentSchema TEST_SCHEMA = new EnvironmentSchema(
        "authorization-profile-test",
        Map.of(
            "root",
            new EnvironmentSchema.Root(new EnvironmentSchema.Field(LanguageType.Scalar.BOOL, false, false), Map.of())
        )
    );

    /**
     * Verifies that Request Authorization retains program constructs while using the Enhancement #237 expression
     * surface.
     *
     * Given: the Request Authorization Compilation Profile.
     * Expect: it uses program mode, keeps local bindings/control flow, and enables only approved expression features.
     */
    @Test
    @DisplayName("defines the request authorization program profile")
    void shouldDefineProgramProfileWhenRequestAuthorizationCompilesPolicies() {
        CompilationProfile expected = new CompilationProfile(
            CompilationMode.PROGRAM,
            Set.of(
                CompilationFeature.LOCAL_BINDINGS,
                CompilationFeature.CONDITIONAL_CONTROL_FLOW,
                CompilationFeature.LIST_LITERALS,
                CompilationFeature.LOGICAL_OPERATORS,
                CompilationFeature.EQUALITY_OPERATORS,
                CompilationFeature.ORDERING_OPERATORS,
                CompilationFeature.CONTAINS_INTRINSIC
            )
        );

        CompilationProfile actual = AuthorizationCompilationProfile.requestPolicy();

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.fingerprint()).isEqualTo(expected.fingerprint());
    }

    /**
     * Verifies that Object Authorization uses the same approved expression surface without statement features.
     *
     * Given: the Object Authorization Compilation Profile.
     * Expect: it uses expression mode and enables no legacy operators/functions.
     */
    @Test
    @DisplayName("defines the object authorization expression profile")
    void shouldDefineExpressionProfileWhenObjectAuthorizationCompilesPolicies() {
        CompilationProfile expected = new CompilationProfile(
            CompilationMode.EXPRESSION,
            Set.of(
                CompilationFeature.LIST_LITERALS,
                CompilationFeature.LOGICAL_OPERATORS,
                CompilationFeature.EQUALITY_OPERATORS,
                CompilationFeature.ORDERING_OPERATORS,
                CompilationFeature.CONTAINS_INTRINSIC
            )
        );

        CompilationProfile actual = AuthorizationCompilationProfile.objectPolicy();

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.fingerprint()).isEqualTo(expected.fingerprint());
    }

    @Test
    @DisplayName("request authorization accepts contains and rejects legacy expression operators")
    void shouldLimitRequestAuthorizationToApprovedExpressionOperators() {
        LanguageCompiler compiler = new LanguageCompiler();
        CompilationProfile profile = AuthorizationCompilationProfile.requestPolicy();

        assertThatCode(() ->
            compiler.compile("return contains(\"alice\", \"ali\");", TEST_SCHEMA, profile)
        ).doesNotThrowAnyException();
        assertRejected(compiler, profile, "return !true;");
        assertRejected(compiler, profile, "return \"alice\" in [\"alice\"];");
        assertRejected(compiler, profile, "return 1 + 1 == 2;");
        assertRejected(compiler, profile, "return any([1], value => value == 1);");
        assertRejected(compiler, profile, "return len(\"alice\") == 5;");
    }

    @Test
    @DisplayName("object authorization accepts contains and rejects legacy expression operators")
    void shouldLimitObjectAuthorizationToApprovedExpressionOperators() {
        LanguageCompiler compiler = new LanguageCompiler();
        CompilationProfile profile = AuthorizationCompilationProfile.objectPolicy();

        assertThatCode(() ->
            compiler.compile("contains(\"alice\", \"ali\")", TEST_SCHEMA, profile)
        ).doesNotThrowAnyException();
        assertRejected(compiler, profile, "!true");
        assertRejected(compiler, profile, "\"alice\" in [\"alice\"]");
        assertRejected(compiler, profile, "1 + 1 == 2");
        assertRejected(compiler, profile, "any([1], value => value == 1)");
        assertRejected(compiler, profile, "len(\"alice\") == 5");
    }

    private static void assertRejected(LanguageCompiler compiler, CompilationProfile profile, String source) {
        assertThatThrownBy(() -> compiler.compile(source, TEST_SCHEMA, profile)).isInstanceOf(
            EmbeddedLanguageException.class
        );
    }
}
