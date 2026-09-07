package io.taskmigo.auth.authorization.object;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.embeddedlanguage.EmbeddedLanguageFilterLowerer;
import io.taskmigo.auth.authorization.request.AuthorizationSnapshot;
import io.taskmigo.auth.authorization.request.StatementArtifactFactory;
import io.taskmigo.auth.authorization.statement.ApiInfo;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.TargetInfo;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageCompiler;
import io.taskmigo.embeddedlanguage.EmbeddedLanguagePartialEvaluator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObjectAuthorizationResultTypeTest {

    private final AuthorizationObjectQueryDialect dialect = new ResultTypeDialect();
    private final EmbeddedLanguageCompiler compiler = new EmbeddedLanguageCompiler();
    private final ObjectAuthorizationService service = new ObjectAuthorizationService(
        new EmbeddedLanguageFilterLowerer(new EmbeddedLanguagePartialEvaluator()),
        this.compiler,
        List.of(this.dialect)
    );

    @Test
    @DisplayName("activates an otherwise valid non-boolean object policy")
    void shouldActivateObjectPolicyWithoutRequiringBooleanResultType() {
        assertThatCode(() -> this.service.validatePolicy("return 1;", "GET", "/api/v0/objects"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a non-boolean object policy only during runtime planning")
    void shouldRejectNonBooleanObjectPolicyDuringRuntimePlanning() {
        StatementInfo statement = new StatementInfo(
            UUID.randomUUID(),
            "non_boolean_object",
            null,
            Effect.ALLOW,
            Scope.OBJECT,
            new TargetInfo(new ApiInfo("GET", "/api/v0/objects")),
            "return 1;"
        );
        AuthorizationSnapshot snapshot = new AuthorizationSnapshot(
            UUID.randomUUID(),
            List.of(statement),
            new StatementArtifactFactory(this.compiler, List.of(this.dialect)).build(List.of(statement)),
            Map.of()
        );

        assertThatThrownBy(() -> this.service.plan(snapshot, "GET", "/api/v0/objects"))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("Bool");
    }

    private static final class ResultTypeDialect implements AuthorizationObjectQueryDialect {
        @Override
        public String method() {
            return "GET";
        }

        @Override
        public String path() {
            return "/api/v0/objects";
        }

        @Override
        public Map<String, Class<?>> fields() {
            return Map.of("age", Integer.class);
        }

        @Override
        public Set<String> nullableFields() {
            return Set.of();
        }
    }
}
