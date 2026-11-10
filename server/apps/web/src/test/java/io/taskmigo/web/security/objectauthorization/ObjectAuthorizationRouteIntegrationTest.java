package io.taskmigo.web.security.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.taskmigo.authorization.object.application.resolution.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.request.application.resolution.EffectiveStatement;
import io.taskmigo.authorization.request.application.resolution.EffectiveStatementResolver;
import io.taskmigo.authorization.request.application.service.StatementArtifactFactory;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.identity.user.application.api.UserAuthorization;
import io.taskmigo.identity.user.application.api.UserService;
import io.taskmigo.web.api.v0.testing.ApiIntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ObjectAuthorizationRouteIntegrationTest extends ApiIntegrationTestSupport {

    private final ObjectAuthorizationTargetResolver resolver;
    private final EffectiveStatementResolver statements;
    private final UserService users;

    ObjectAuthorizationRouteIntegrationTest(
        ObjectAuthorizationTargetResolver resolver,
        EffectiveStatementResolver statements,
        UserService users
    ) {
        this.resolver = resolver;
        this.statements = statements;
        this.users = users;
    }

    @Test
    @DisplayName("compiles the managed User statement mutation policy against the runtime Authorization Contract")
    void shouldCompileUserStatementMutationPolicyAgainstRuntimeOperationRoute() {
        String target = "/api/v0/users/.*/statements";
        StatementTargetPathMatcher matcher = StatementTargetPathMatcher.compile(target);

        assertThat(this.resolver.applicable("PATCH", matcher))
            .singleElement()
            .isInstanceOf(UserAuthorization.UpdateStatements.class);

        StatementInfo denyRetained = new StatementInfo(
            UUID.randomUUID(),
            "user_statement_mutation_deny_retained",
            null,
            Effect.DENY,
            Scope.OBJECT,
            new TargetInfo(new ApiInfo("PATCH", target)),
            "object.status == \"RETAINED\""
        );
        StatementArtifactFactory factory = new StatementArtifactFactory(this.resolver);

        assertThatCode(() ->
            factory.build(
                List.of(new EffectiveStatement(denyRetained)),
                "PATCH",
                "/api/v0/users/" + UUID.randomUUID() + "/statements"
            )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("compiles every effective System User statement for a User statement mutation request")
    void shouldCompileEveryEffectiveSystemStatementForUserStatementMutation() {
        UUID systemUserId = this.users.findForAuthentication("system").orElseThrow().id();
        String requestPath = "/api/v0/users/" + UUID.randomUUID() + "/statements";
        StatementArtifactFactory factory = new StatementArtifactFactory(this.resolver);
        List<EffectiveStatement> effective = this.statements.resolve(systemUserId);

        assertThat(effective).isNotEmpty();
        for (EffectiveStatement statement : effective) {
            assertThatCode(() -> factory.build(List.of(statement), "PATCH", requestPath))
                .as("effective Statement %s", statement.statement().code())
                .doesNotThrowAnyException();
        }
        assertThatCode(() -> factory.build(effective, "PATCH", requestPath))
            .as("complete effective Statement set")
            .doesNotThrowAnyException();
    }
}
