package io.taskmigo.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistry;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.language.EmbeddedLanguageCompiler;
import io.taskmigo.language.EmbeddedLanguageEvaluator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestAuthorizationResultTypeTest {

    /**
     * Verifies that request authorization fails closed when a policy does not produce a boolean result.
     *
     * Given: a matching allow Statement whose policy returns the string `allow`.
     * Expect: the authorization decision is denied rather than treating the string as a grant.
     */
    @Test
    @DisplayName("fails closed when a request policy evaluates to a non-boolean value")
    void shouldDenyRequestWhenPolicyResultIsNotBoolean() {
        // Arrange
        EffectiveStatementResolver resolver = mock(EffectiveStatementResolver.class);
        UUID userId = UUID.randomUUID();
        StatementInfo statement = new StatementInfo(
            UUID.randomUUID(),
            "non_boolean_request",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", "/api/v0/users")),
            "return \"allow\";"
        );
        when(resolver.resolve(userId)).thenReturn(List.of(statement));
        RequestAuthorizationService service = new RequestAuthorizationService(
            resolver,
            new EmbeddedLanguageEvaluator(),
            new StatementArtifactFactory(
                new EmbeddedLanguageCompiler(),
                List.of(),
                ObjectAuthorizationSchemaRegistry.all(List.of())
            )
        );

        // Act
        RequestAuthorizationDecision result = service.authorize(userId, "GET", "/api/v0/users", Map.of());

        // Assert
        assertThat(result.allowed()).isFalse();
    }
}
