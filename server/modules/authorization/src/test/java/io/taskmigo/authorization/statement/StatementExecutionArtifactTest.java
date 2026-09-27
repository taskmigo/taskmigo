package io.taskmigo.authorization.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;

import io.taskmigo.language.CompiledSource;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementExecutionArtifactTest {

    /**
     * Verifies target matching uses complete path regex semantics and ignores query strings.
     *
     * Given: a GET Statement targeting a numeric user path.
     * Expect: the exact path with a query matches, a longer suffix does not, and method comparison is case-sensitive.
     */
    @Test
    @DisplayName("matches complete request path without query string")
    void shouldMatchCompletePathWhenQueryStringIsPresent() {
        // Arrange
        StatementExecutionArtifact artifact = artifact(statement("GET", "/api/v0/users/[0-9]+"));

        // Act
        boolean exactMatch = artifact.matches("GET", "/api/v0/users/42?active=true");
        boolean suffixMatch = artifact.matches("GET", "/api/v0/users/42/extra");
        boolean lowercaseMethodMatch = artifact.matches("get", "/api/v0/users/42");

        // Assert
        assertThat(exactMatch).isTrue();
        assertThat(suffixMatch).isFalse();
        assertThat(lowercaseMethodMatch).isFalse();
    }

    /**
     * Verifies the wildcard method retains its established runtime meaning.
     *
     * Given: a Statement target whose HTTP method is `*`.
     * Expect: both GET and DELETE requests match the same target path.
     */
    @Test
    @DisplayName("matches every method for wildcard statement target")
    void shouldMatchEveryMethodWhenTargetMethodIsWildcard() {
        // Arrange
        StatementExecutionArtifact artifact = artifact(statement("*", "/api/v0/users"));

        // Act
        boolean getMatches = artifact.matches("GET", "/api/v0/users");
        boolean deleteMatches = artifact.matches("DELETE", "/api/v0/users");

        // Assert
        assertThat(getMatches).isTrue();
        assertThat(deleteMatches).isTrue();
    }

    /**
     * Verifies that a known catastrophic-backtracking pattern cannot consume an
     * unbounded amount of authorization request time.
     *
     * Given: a non-matching path of 2,001 characters and the pathological
     * `(a|aa)+$` target pattern.
     * Expect: matching completes within one second and reports no match.
     */
    @Test
    @DisplayName("bounds pathological target regex matching time")
    void shouldBoundTargetMatchingTimeWhenPatternHasAmbiguousRepetition() {
        // Arrange
        StatementExecutionArtifact artifact = artifact(statement("GET", "(a|aa)+$"));
        String requestPath = "a".repeat(2_000) + "!";

        // Act + Assert
        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
            assertThat(artifact.matches("GET", requestPath)).isFalse()
        );
    }

    /**
     * Verifies that nested repetitions cannot consume an unbounded amount of
     * authorization request time.
     *
     * Given: a non-matching path of 2,001 characters and the pathological
     * `(a+)+$` target pattern.
     * Expect: matching completes within one second and reports no match.
     */
    @Test
    @DisplayName("bounds nested target regex repetition matching time")
    void shouldBoundTargetMatchingTimeWhenPatternHasNestedRepetition() {
        // Arrange
        StatementExecutionArtifact artifact = artifact(statement("GET", "(a+)+$"));
        String requestPath = "a".repeat(2_000) + "!";

        // Act + Assert
        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
            assertThat(artifact.matches("GET", requestPath)).isFalse()
        );
    }

    /**
     * Verifies that target expressions cannot use regex constructs that require
     * backtracking support.
     *
     * Given: a target expression containing a positive lookahead.
     * Expect: compilation rejects the expression before it can enter the
     * authorization matching path.
     */
    @Test
    @DisplayName("rejects lookarounds in statement target expressions")
    void shouldRejectTargetPatternWhenItUsesLookaround() {
        // Arrange

        // Act + Assert
        assertThatThrownBy(() -> StatementTargetPattern.compile("/api/v0/users(?=/active)"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static StatementExecutionArtifact artifact(StatementInfo statement) {
        return new StatementExecutionArtifact(
            statement,
            mock(CompiledSource.class),
            StatementTargetPattern.compile(statement.target().api().path())
        );
    }

    private static StatementInfo statement(String method, String path) {
        return new StatementInfo(
            UUID.randomUUID(),
            "users_read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo(method, path)),
            "return true;"
        );
    }
}
