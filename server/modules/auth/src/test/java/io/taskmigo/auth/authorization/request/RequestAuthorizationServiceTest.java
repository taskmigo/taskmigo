package io.taskmigo.auth.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.auth.authorization.condition.AuthorizationCompiler;
import io.taskmigo.auth.authorization.condition.AuthorizationExpressionEvaluator;
import io.taskmigo.auth.authorization.statement.ApiInfo;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.TargetInfo;
import io.taskmigo.auth.authorization.statement.TargetType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestAuthorizationServiceTest {

    private final EffectiveStatementResolver statements = mock(EffectiveStatementResolver.class);
    private final RequestAuthorizationService service = new RequestAuthorizationService(
        this.statements,
        new AuthorizationCompiler(),
        new AuthorizationExpressionEvaluator()
    );

    /**
     * Verifies that a matching request allow Statement grants access.
     *
     * Given: an unconditional GET request Statement matching `/api/v0/users`.
     * Expect: the request authorization decision is allowed.
     */
    @Test
    @DisplayName("allows a request when a matching allow statement exists")
    void shouldAllowRequestWhenMatchingAllowStatementExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(this.statements.resolve(userId)).thenReturn(List.of(statement(Effect.ALLOW)));

        // Act
        RequestAuthorizationDecision result = this.service.authorize(
            userId,
            "GET",
            "/api/v0/users",
            Map.of("request", Map.of("method", "GET"))
        );

        // Assert
        assertThat(result.allowed()).isTrue();
    }

    /**
     * Verifies that a matching deny Statement overrides a matching allow Statement.
     *
     * Given: unconditional allow and deny Statements for the same request.
     * Expect: the request authorization decision is denied.
     */
    @Test
    @DisplayName("denies a request when a matching deny statement exists")
    void shouldDenyRequestWhenMatchingDenyStatementExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(this.statements.resolve(userId)).thenReturn(List.of(statement(Effect.ALLOW), statement(Effect.DENY)));

        // Act
        RequestAuthorizationDecision result = this.service.authorize(
            userId,
            "GET",
            "/api/v0/users",
            Map.of("request", Map.of("method", "GET"))
        );

        // Assert
        assertThat(result.allowed()).isFalse();
    }

    private static StatementInfo statement(Effect effect) {
        return new StatementInfo(
            UUID.randomUUID(),
            "statement-" + UUID.randomUUID(),
            null,
            effect,
            new TargetInfo(TargetType.REQUEST, new ApiInfo("GET", "/api/v0/users")),
            List.of("true")
        );
    }
}
