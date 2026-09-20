package io.taskmigo.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;\nimport static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.core.AuthorizationException;\nimport io.taskmigo.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.spi.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageContract;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementArtifactFactoryTest {

    private final StatementArtifactFactory factory = new StatementArtifactFactory(
        new LanguageCompiler(),
        List.of(),
        ObjectAuthorizationTargetResolver.all(List.of())
    );

    /**
     * Verifies that persisted Statement revisions control cross-operation derivative reuse.
     *
     * Given: one Statement loaded twice with the same `updated_at` and once with a newer revision.
     * Expect: the unchanged revision shares both derivatives, while the newer revision receives new derivatives.
     */
    @Test
    @DisplayName("reuses artifacts only while statement updated_at is unchanged")
    void shouldReuseDerivedArtifactsWhenStatementUpdatedAtIsUnchanged() {
        // Arrange
        UUID id = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-09-13T00:00:00Z");
        EffectiveStatement original = effective(statement(id, Effect.ALLOW, "/api/v0/users"), updatedAt);
        EffectiveStatement unchanged = effective(statement(id, Effect.ALLOW, "/api/v0/users"), updatedAt);
        EffectiveStatement changed = effective(statement(id, Effect.DENY, "/api/v0/users"), updatedAt.plusSeconds(1));

        // Act
        StatementExecutionArtifact first = this.factory.build(List.of(original), "GET", "/api/v0/users").getFirst();
        StatementExecutionArtifact second = this.factory.build(List.of(unchanged), "GET", "/api/v0/users").getFirst();
        StatementExecutionArtifact different = this.factory.build(List.of(changed), "GET", "/api/v0/users").getFirst();

        // Assert
        assertThat(second.policy()).isSameAs(first.policy());
        assertThat(second.pathMatcher()).isSameAs(first.pathMatcher());
        assertThat(different.policy()).isNotSameAs(first.policy());
        assertThat(different.pathMatcher()).isNotSameAs(first.pathMatcher());
    }

    /**
     * Verifies that the cache retains only the newest observed revision for each Statement id.
     *
     * Given: revision one, revision two, then a stale revision-one snapshot for the same Statement.
     * Expect: the stale snapshot is compiled for its own operation without resurrecting revision one in the cache, and
     * the next revision-two lookup still reuses the revision-two derivatives.
     */
    @Test
    @DisplayName("does not retain historical statement revisions")
    void shouldRetainOnlyNewestObservedStatementRevision() {
        // Arrange
        UUID id = UUID.randomUUID();
        Instant firstUpdatedAt = Instant.parse("2026-09-13T00:00:00Z");
        EffectiveStatement firstRevision = effective(statement(id, Effect.ALLOW, "/api/v0/users"), firstUpdatedAt);
        EffectiveStatement secondRevision = effective(
            statement(id, Effect.DENY, "/api/v0/users"),
            firstUpdatedAt.plusSeconds(1)
        );

        // Act
        StatementExecutionArtifact first = this.factory.build(List.of(firstRevision), "GET", "/api/v0/users").getFirst();
        StatementExecutionArtifact second = this.factory.build(List.of(secondRevision), "GET", "/api/v0/users").getFirst();
        StatementExecutionArtifact stale = this.factory.build(List.of(firstRevision), "GET", "/api/v0/users").getFirst();
        StatementExecutionArtifact latest = this.factory.build(List.of(secondRevision), "GET", "/api/v0/users").getFirst();

        // Assert
        assertThat(stale.policy()).isNotSameAs(first.policy());
        assertThat(stale.pathMatcher()).isNotSameAs(first.pathMatcher());
        assertThat(latest.policy()).isSameAs(second.policy());
        assertThat(latest.pathMatcher()).isSameAs(second.pathMatcher());
    }

    /**
     * Verifies that compiled Statement artifacts carry the current Language and Authorization profile contracts.
     *
     * Given: one request Statement compiled by the artifact factory.
     * Expect: its public compiled-source metadata records collision-resistant source identity, the centralized Language
     * contract, and the Authorization-owned profile fingerprint without exposing the concrete Semantic AST.
     */
    @Test
    @DisplayName("includes language and authorization profile identity in artifacts")
    void shouldIncludeLanguageAndProfileIdentityWhenStatementIsCompiled() {
        // Arrange
        EffectiveStatement statement = effective(
            statement(UUID.randomUUID(), Effect.ALLOW, "/api/v0/users"),
            Instant.EPOCH
        );

        // Act
        StatementExecutionArtifact artifact = this.factory.build(List.of(statement), "GET", "/api/v0/users").getFirst();

        // Assert
        assertThat(artifact.policy().sourceFingerprint()).hasSize(64);
        assertThat(artifact.policy().compilerFingerprint()).contains(LanguageContract.VERSION);
        assertThat(artifact.policy().profileFingerprint()).isEqualTo(
            AuthorizationCompilationProfile.policy().fingerprint()
        );
    }

    /**
     * Verifies semantic policy validation is deferred until a persisted Statement target matches the operation.
     *
     * Given: malformed policy source on a Statement targeting a different request path.
     * Expect: the unrelated operation skips compilation, while a matching operation fails closed during artifact build.
     */
    @Test
    @DisplayName("compiles policy only after statement target matches")
    void shouldDeferPolicyCompilationUntilStatementTargetMatches() {
        // Arrange
        StatementInfo invalid = new StatementInfo(
            UUID.randomUUID(),
            "invalid_policy",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", "/api/v0/admin")),
            "return request.method == ;"
        );
        EffectiveStatement effective = effective(invalid, Instant.EPOCH);

        // Act
        List<StatementExecutionArtifact> unrelated = this.factory.build(
            List.of(effective),
            "GET",
            "/api/v0/users"
        );

        // Assert
        assertThat(unrelated).isEmpty();
        assertThatThrownBy(() -> this.factory.build(List.of(effective), "GET", "/api/v0/admin"))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("Invalid Statement policy");
    }

    private static EffectiveStatement effective(StatementInfo statement, Instant updatedAt) {
        return new EffectiveStatement(statement, updatedAt);
    }

    private static StatementInfo statement(UUID id, Effect effect, String path) {
        return new StatementInfo(
            id,
            "statement",
            null,
            effect,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", path)),
            "return true;"
        );
    }
}
