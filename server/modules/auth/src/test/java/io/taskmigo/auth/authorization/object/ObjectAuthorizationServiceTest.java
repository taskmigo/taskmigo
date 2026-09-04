package io.taskmigo.auth.authorization.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.auth.authorization.condition.AuthorizationExpressionEvaluator;
import io.taskmigo.auth.authorization.request.EffectiveStatementResolver;
import io.taskmigo.auth.authorization.statement.ApiInfo;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.TargetInfo;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObjectAuthorizationServiceTest {

    private final EffectiveStatementResolver statements = mock(EffectiveStatementResolver.class);
    private final AuthorizationObjectQueryDialect dialect = new TestDialect();
    private final ObjectAuthorizationService service = new ObjectAuthorizationService(
        this.statements,
        List.of(this.dialect)
    );
    private final AuthorizationExpressionEvaluator evaluator = new AuthorizationExpressionEvaluator();

    /**
     * Verifies that an unconditional object Statement contributes an allow predicate to the object plan.
     *
     * Given: an unconditional allow Statement targeting the queried object operation.
     * Expect: the plan contains one matching Statement and evaluates its allow predicate as true.
     */
    @Test
    @DisplayName("specializes principal values in object predicates")
    void shouldSpecializePrincipalWhenBuildingObjectPlan() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(this.statements.resolve(userId)).thenReturn(List.of(statement(Effect.ALLOW)));

        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(
            userId,
            "GET",
            "/api/v0/objects",
            roots(userId.toString(), "GET", UUID.randomUUID())
        );

        // Assert
        assertThat(plan.matchedStatements()).hasSize(1);
        assertThat(
            this.evaluator.evaluate(plan.predicate(), Map.of("object", Map.of("id", userId.toString())))
        ).isTrue();
    }

    /**
     * Verifies that an object deny predicate overrides an otherwise matching allow predicate.
     *
     * Given: unconditional allow and deny Statements targeting the same object operation.
     * Expect: the combined database predicate evaluates false for the object, regardless of the allow Statement.
     */
    @Test
    @DisplayName("applies deny override to object predicates")
    void shouldDenyObjectWhenMatchingDenyStatementExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(this.statements.resolve(userId)).thenReturn(List.of(statement(Effect.ALLOW), statement(Effect.DENY)));

        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(
            userId,
            "GET",
            "/api/v0/objects",
            roots(userId.toString(), "GET", UUID.randomUUID())
        );

        // Assert
        assertThat(
            this.evaluator.evaluate(plan.predicate(), Map.of("object", Map.of("id", UUID.randomUUID())))
        ).isFalse();
    }

    private static StatementInfo statement(Effect effect) {
        return new StatementInfo(
            UUID.randomUUID(),
            "statement-" + UUID.randomUUID(),
            null,
            effect,
            Scope.OBJECT,
            new TargetInfo(new ApiInfo("GET", "/api/v0/objects")),
            null
        );
    }

    private static Map<String, ?> roots(String userId, String method, UUID objectId) {
        return Map.of(
            "principal",
            Map.of("id", userId),
            "request",
            Map.of("method", method),
            "object",
            Map.of("id", objectId)
        );
    }

    private static final class TestDialect implements AuthorizationObjectQueryDialect {

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
            return Map.of("id", String.class);
        }
    }
}
