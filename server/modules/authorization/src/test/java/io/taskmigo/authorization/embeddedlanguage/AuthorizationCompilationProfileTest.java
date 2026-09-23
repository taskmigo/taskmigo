package io.taskmigo.authorization.embeddedlanguage;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.language.CompilationFeature;
import io.taskmigo.language.CompilationMode;
import io.taskmigo.language.CompilationProfile;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorizationCompilationProfileTest {

    /**
     * Verifies that Request Authorization retains the bounded program compilation contract.
     *
     * Given: the Request Authorization Compilation Profile.
     * Expect: it uses program mode with the explicit Authorization feature allowlist.
     */
    @Test
    @DisplayName("defines the request authorization program profile")
    void shouldDefineProgramProfileWhenRequestAuthorizationCompilesPolicies() {
        // Arrange
        CompilationProfile expected = new CompilationProfile(
            CompilationMode.PROGRAM,
            Set.of(
                CompilationFeature.LOCAL_BINDINGS,
                CompilationFeature.CONDITIONAL_CONTROL_FLOW,
                CompilationFeature.LIST_LITERALS,
                CompilationFeature.MEMBERSHIP,
                CompilationFeature.LOGICAL_OPERATORS,
                CompilationFeature.EQUALITY_OPERATORS,
                CompilationFeature.ORDERING_OPERATORS,
                CompilationFeature.ARITHMETIC_OPERATORS,
                CompilationFeature.COLLECTION_QUANTIFIERS,
                CompilationFeature.LENGTH_INTRINSIC
            )
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
        CompilationProfile expected = new CompilationProfile(
            CompilationMode.EXPRESSION,
            Set.of(
                CompilationFeature.LIST_LITERALS,
                CompilationFeature.MEMBERSHIP,
                CompilationFeature.LOGICAL_OPERATORS,
                CompilationFeature.EQUALITY_OPERATORS,
                CompilationFeature.ORDERING_OPERATORS,
                CompilationFeature.ARITHMETIC_OPERATORS,
                CompilationFeature.COLLECTION_QUANTIFIERS,
                CompilationFeature.LENGTH_INTRINSIC
            )
        );

        // Act
        CompilationProfile actual = AuthorizationCompilationProfile.objectPolicy();

        // Assert
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.fingerprint()).isEqualTo(expected.fingerprint());
    }
}
