package io.taskmigo.authorization.request.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.request.application.port.out.EffectiveStatement;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.language.LanguageCompiler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementArtifactFactoryTest {

    private final StatementArtifactFactory factory = new StatementArtifactFactory(
        new LanguageCompiler(),
        ObjectAuthorizationTargetResolver.all(List.of())
    );

    @Test
    @DisplayName("recompiles authoritative statements without cache reuse")
    void shouldRecompileAuthoritativeStatementWithoutCacheReuse() {
        EffectiveStatement statement = effective(statement(UUID.randomUUID(), Effect.ALLOW, "/api/v0/users"));
        StatementExecutionArtifact first = this.factory.build(List.of(statement), "GET", "/api/v0/users").getFirst();
        StatementExecutionArtifact second = this.factory.build(List.of(statement), "GET", "/api/v0/users").getFirst();
        assertThat(second.policy()).isNotSameAs(first.policy());
        assertThat(second.pathMatcher()).isNotSameAs(first.pathMatcher());
    }

    @Test
    @DisplayName("compiles request statements with the request profile")
    void shouldCompileRequestStatementWithRequestProfile() {
        StatementExecutionArtifact artifact = this.factory
            .build(
                List.of(effective(statement(UUID.randomUUID(), Effect.ALLOW, "/api/v0/users"))),
                "GET",
                "/api/v0/users"
            )
            .getFirst();
        assertThat(artifact.policy().profileFingerprint()).isEqualTo(
            AuthorizationCompilationProfile.requestPolicy().fingerprint()
        );
    }

    @Test
    @DisplayName("compiles policies only after the statement target matches")
    void shouldCompileOnlyAfterTargetMatches() {
        StatementInfo invalid = new StatementInfo(
            UUID.randomUUID(),
            "invalid",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", "/api/v0/admin")),
            "return request.method == ;"
        );
        EffectiveStatement effective = effective(invalid);
        assertThat(this.factory.build(List.of(effective), "GET", "/api/v0/users")).isEmpty();
        assertThatThrownBy(() -> this.factory.build(List.of(effective), "GET", "/api/v0/admin"))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("Invalid Statement policy");
    }

    private static EffectiveStatement effective(StatementInfo statement) {
        return new EffectiveStatement(statement);
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
