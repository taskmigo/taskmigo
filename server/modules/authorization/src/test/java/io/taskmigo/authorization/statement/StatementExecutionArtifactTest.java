package io.taskmigo.authorization.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.taskmigo.language.CompiledSource;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
     * Verifies pathological target patterns stay bounded on the authorization matching hot path.
     *
     * Given: regular expressions that cause catastrophic backtracking in Java's matcher and a long near-match.
     * Expect: each full-match evaluation returns false within the performance budget.
     */
    @ParameterizedTest
    @ValueSource(strings = { "^/(a+)+$", "^/(a|aa)+$" })
    @DisplayName("bounds pathological target regex matching")
    void shouldCompleteWithinBudgetWhenTargetRegexCanBacktrackPathologically(String targetPath) {
        // Arrange
        StatementExecutionArtifact artifact = artifact(statement("GET", targetPath));
        String requestPath = "/" + "a".repeat(20_000) + "!";

        // Act
        long startedAt = System.nanoTime();
        boolean matches = artifact.matches("GET", requestPath);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // Assert
        assertThat(matches).isFalse();
        assertThat(elapsed).isLessThan(Duration.ofMillis(250));
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

    private static StatementExecutionArtifact artifact(StatementInfo statement) {
        return new StatementExecutionArtifact(
            statement,
            mock(CompiledSource.class),
            StatementTargetPathMatcher.compile(statement.target().api().path())
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
