package io.taskmigo.auth.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static StatementInfo statement(UUID id, Effect effect, String path) {
        return new StatementInfo(
            id,
            "statement",
            null,
            effect,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", path)),
            "export default () => true;"
        );
    }
}
