package io.taskmigo.auth.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.statement.ApiInfo;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.TargetInfo;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementArtifactFactoryTest {

    private final StatementArtifactFactory factory = new StatementArtifactFactory(new JavaScriptPolicyCompiler());

    /**
     * Verifies that derived policy and matcher artifacts are reused only for identical persisted execution state.
     *
     * Given: one database-loaded Statement, the same Statement again, and a copy with a changed effect.
     * Expect: the identical state shares both derivatives, while the changed state receives new derivatives.
     */
    @Test
    @DisplayName("reuses artifacts only for an unchanged statement fingerprint")
    void shouldReuseDerivedArtifactsWhenStatementExecutionStateIsUnchanged() {
        // Arrange
        UUID id = UUID.randomUUID();
        StatementInfo original = statement(id, Effect.ALLOW, "/api/v0/users");
        StatementInfo unchanged = statement(id, Effect.ALLOW, "/api/v0/users");
        StatementInfo changed = statement(id, Effect.DENY, "/api/v0/users");

        // Act
        StatementExecutionArtifact first = this.factory.build(List.of(original)).getFirst();
        StatementExecutionArtifact second = this.factory.build(List.of(unchanged)).getFirst();
        StatementExecutionArtifact different = this.factory.build(List.of(changed)).getFirst();

        // Assert
        assertThat(second.policy()).isSameAs(first.policy());
        assertThat(second.pathMatcher()).isSameAs(first.pathMatcher());
        assertThat(different.policy()).isNotSameAs(first.policy());
        assertThat(different.pathMatcher()).isNotSameAs(first.pathMatcher());
    }

    /**
     * Verifies that immutable derived artifacts are shared between different Statements with identical compilation
     * inputs while each executable artifact keeps its own Statement metadata.
     *
     * Given: two database-loaded Statements with different ids but the same policy, scope, and target path.
     * Expect: both artifacts share the Policy IR and Pattern instances, while their StatementInfo instances remain
     * distinct.
     */
    @Test
    @DisplayName("shares immutable artifacts when statements have identical compilation inputs")
    void shouldShareImmutableArtifactsWhenStatementsHaveIdenticalCompilationInputs() {
        // Arrange
        StatementInfo firstStatement = statement(UUID.randomUUID(), Effect.ALLOW, Scope.REQUEST, "/api/v0/users");
        StatementInfo secondStatement = statement(UUID.randomUUID(), Effect.DENY, Scope.REQUEST, "/api/v0/users");

        // Act
        List<StatementExecutionArtifact> artifacts = this.factory.build(List.of(firstStatement, secondStatement));

        // Assert
        assertThat(artifacts.get(0).policy()).isSameAs(artifacts.get(1).policy());
        assertThat(artifacts.get(0).pathMatcher()).isSameAs(artifacts.get(1).pathMatcher());
        assertThat(artifacts.get(0).statement()).isSameAs(firstStatement);
        assertThat(artifacts.get(1).statement()).isSameAs(secondStatement);
    }

    /**
     * Verifies that a policy compiled for one Statement scope is never reused for another scope.
     *
     * Given: a Request Statement and an Object Statement with the same policy source and target path.
     * Expect: their Policy IR instances differ because scope participates in policy compilation, while the path
     * matcher may still be shared.
     */
    @Test
    @DisplayName("does not share policy artifacts across scopes")
    void shouldNotSharePolicyArtifactsWhenStatementsHaveDifferentScopes() {
        // Arrange
        StatementInfo requestStatement = statement(UUID.randomUUID(), Effect.ALLOW, Scope.REQUEST, "/api/v0/users");
        StatementInfo objectStatement = statement(UUID.randomUUID(), Effect.ALLOW, Scope.OBJECT, "/api/v0/users");

        // Act
        List<StatementExecutionArtifact> artifacts = this.factory.build(List.of(requestStatement, objectStatement));

        // Assert
        assertThat(artifacts.get(0).policy()).isNotSameAs(artifacts.get(1).policy());
        assertThat(artifacts.get(0).pathMatcher()).isSameAs(artifacts.get(1).pathMatcher());
    }

    /**
     * Verifies that a different policy source creates a different immutable Policy IR instead of reusing a stale
     * compilation.
     *
     * Given: two Request Statements with the same target path and different constant policy sources.
     * Expect: their Policy IR instances differ while their identical target paths share the Pattern instance.
     */
    @Test
    @DisplayName("does not share policy artifacts when policy sources differ")
    void shouldNotSharePolicyArtifactsWhenPolicySourcesDiffer() {
        // Arrange
        StatementInfo allowingStatement = statement(
            UUID.randomUUID(),
            Effect.ALLOW,
            Scope.REQUEST,
            "/api/v0/users",
            "export default () => true;"
        );
        StatementInfo denyingStatement = statement(
            UUID.randomUUID(),
            Effect.ALLOW,
            Scope.REQUEST,
            "/api/v0/users",
            "export default () => false;"
        );

        // Act
        List<StatementExecutionArtifact> artifacts = this.factory.build(List.of(allowingStatement, denyingStatement));

        // Assert
        assertThat(artifacts.get(0).policy()).isNotSameAs(artifacts.get(1).policy());
        assertThat(artifacts.get(0).pathMatcher()).isSameAs(artifacts.get(1).pathMatcher());
    }

    /**
     * Verifies that a failed path compilation does not poison the Statement artifact cache for a later valid state.
     *
     * Given: one Statement id first using an invalid regular expression and then using a valid target path.
     * Expect: the invalid build fails, and the valid state can subsequently build an executable artifact.
     */
    @Test
    @DisplayName("does not cache an artifact when target path compilation fails")
    void shouldBuildValidArtifactWhenEarlierPathCompilationFailed() {
        // Arrange
        UUID id = UUID.randomUUID();
        StatementInfo invalid = statement(id, Effect.ALLOW, Scope.REQUEST, "[");
        StatementInfo valid = statement(id, Effect.ALLOW, Scope.REQUEST, "/api/v0/users");

        // Act + Assert
        assertThatThrownBy(() -> this.factory.build(List.of(invalid)))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("regular expression");

        // Act
        StatementExecutionArtifact artifact = this.factory.build(List.of(valid)).getFirst();

        // Assert
        assertThat(artifact.statement()).isSameAs(valid);
    }

    /**
     * Verifies that a failed policy compilation does not poison the Statement artifact cache for a later valid state.
     *
     * Given: one Statement id first using an invalid default export and then using a valid boolean policy.
     * Expect: the invalid build fails, and the valid state can subsequently build an executable artifact.
     */
    @Test
    @DisplayName("does not cache an artifact when policy compilation fails")
    void shouldBuildValidArtifactWhenEarlierPolicyCompilationFailed() {
        // Arrange
        UUID id = UUID.randomUUID();
        StatementInfo invalid = statement(id, Effect.ALLOW, Scope.REQUEST, "/api/v0/users", "export default true;");
        StatementInfo valid = statement(id, Effect.ALLOW, Scope.REQUEST, "/api/v0/users");

        // Act + Assert
        assertThatThrownBy(() -> this.factory.build(List.of(invalid)))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("function");

        // Act
        StatementExecutionArtifact artifact = this.factory.build(List.of(valid)).getFirst();

        // Assert
        assertThat(artifact.statement()).isSameAs(valid);
    }

    private static StatementInfo statement(UUID id, Effect effect, String path) {
        return statement(id, effect, Scope.REQUEST, path);
    }

    private static StatementInfo statement(UUID id, Effect effect, Scope scope, String path) {
        return statement(id, effect, scope, path, "export default () => true;");
    }

    private static StatementInfo statement(UUID id, Effect effect, Scope scope, String path, String policy) {
        return new StatementInfo(
            id,
            "statement",
            null,
            effect,
            scope,
            new TargetInfo(new ApiInfo("GET", path)),
            policy
        );
    }
}
