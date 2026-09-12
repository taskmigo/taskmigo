package io.taskmigo.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistry;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageContract;
import io.taskmigo.language.SemanticAst;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementArtifactFactoryTest {

    private final StatementArtifactFactory factory = new StatementArtifactFactory(
        new LanguageCompiler(),
        List.of(),
        ObjectAuthorizationSchemaRegistry.all(List.of())
    );

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
     * Verifies that compiled Statement artifacts carry the current Language and Authorization profile contracts.
     *
     * Given: one request Statement compiled by the artifact factory.
     * Expect: its metadata records the centralized Language contract and the Authorization-owned profile fingerprint,
     * while the Semantic AST exposes no independent version component.
     */
    @Test
    @DisplayName("includes language and authorization profile identity in artifacts")
    void shouldIncludeLanguageAndProfileIdentityWhenStatementIsCompiled() {
        // Arrange
        StatementInfo statement = statement(UUID.randomUUID(), Effect.ALLOW, "/api/v0/users");

        // Act
        StatementExecutionArtifact artifact = this.factory.build(List.of(statement)).getFirst();

        // Assert
        assertThat(artifact.policy().compilerFingerprint()).contains(LanguageContract.VERSION);
        assertThat(
            Arrays.stream(Objects.requireNonNull(SemanticAst.class.getRecordComponents())).map(RecordComponent::getName)
        ).doesNotContain("languageVersion");
        assertThat(artifact.policy().profileFingerprint()).isEqualTo(
            AuthorizationCompilationProfile.policy().fingerprint()
        );
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
