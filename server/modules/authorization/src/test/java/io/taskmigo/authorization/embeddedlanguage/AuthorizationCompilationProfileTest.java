package io.taskmigo.authorization.embeddedlanguage;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.language.CompilationFeature;
import io.taskmigo.language.CompilationMode;
import io.taskmigo.language.CompilationProfile;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorizationCompilationProfileTest {

    /**
     * Verifies that Request Authorization retains the bounded program compilation contract.
     *
     * Given: the Request Authorization Compilation Profile.
     * Expect: it uses program mode with every Language feature family enabled.
     */
    @Test
    @DisplayName("defines the request authorization program profile")
    void shouldDefineProgramProfileWhenRequestAuthorizationCompilesPolicies() {
        // Arrange
        CompilationProfile expected = new CompilationProfile(
            CompilationMode.PROGRAM,
            EnumSet.allOf(CompilationFeature.class)
        );

        // Act
        CompilationProfile actual = AuthorizationCompilationProfile.requestPolicy();

        // Assert
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.fingerprint()).isEqualTo(expected.fingerprint());
    }

    /**
     * Verifies that Object Authorization accepts only expression-shaped policy source.
     *
     * Given: the Object Authorization Compilation Profile.
     * Expect: it uses expression mode and excludes statement-only local bindings and conditional control flow.
     */
    @Test
    @DisplayName("defines the object authorization expression profile")
    void shouldDefineExpressionProfileWhenObjectAuthorizationCompilesPolicies() {
        // Arrange
        EnumSet<CompilationFeature> features = EnumSet.allOf(CompilationFeature.class);
        features.remove(CompilationFeature.LOCAL_BINDINGS);
        features.remove(CompilationFeature.CONDITIONAL_CONTROL_FLOW);
        CompilationProfile expected = new CompilationProfile(CompilationMode.EXPRESSION, features);

        // Act
        CompilationProfile actual = AuthorizationCompilationProfile.objectPolicy();

        // Assert
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.fingerprint()).isEqualTo(expected.fingerprint());
    }
}
