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
     * Verifies that Authorization owns one bounded program profile rather than inheriting a generic compiler default.
     *
     * Given: the Authorization Compilation Profile.
     * Expect: it uses program mode and enables exactly the bounded policy feature families.
     */
    @Test
    @DisplayName("defines the bounded authorization compilation profile")
    void shouldDefineBoundedPolicyProfileWhenAuthorizationCompilesPolicies() {
        // Arrange
        CompilationProfile expected = new CompilationProfile(
            CompilationMode.PROGRAM,
            EnumSet.allOf(CompilationFeature.class)
        );

        // Act
        CompilationProfile actual = AuthorizationCompilationProfile.policy();

        // Assert
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.fingerprint()).isEqualTo(expected.fingerprint());
    }
}
