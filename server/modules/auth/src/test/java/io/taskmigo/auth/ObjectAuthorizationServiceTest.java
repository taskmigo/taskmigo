package io.taskmigo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        new AuthorizationCompiler(),
        List.of(this.dialect)
    );
    private final AuthorizationExpressionEvaluator evaluator = new AuthorizationExpressionEvaluator();

    /**
     * Verifies that object plans specialize non-object values while retaining object fields for database translation.
     *
     * Given: an allow Statement requiring object.id to equal the requesting principal id.
     * Expect: the plan allows the matching object and its predicate still contains an object reference.
     */
    @Test
    @DisplayName("specializes principal values in object predicates")
    void shouldSpecializePrincipalWhenBuildingObjectPlan() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(this.statements.resolve(userId)).thenReturn(
            List.of(statement(StatementService.Effect.ALLOW, "object.id == principal.id"))
        );

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
        when(this.statements.resolve(userId)).thenReturn(
            List.of(statement(StatementService.Effect.ALLOW), statement(StatementService.Effect.DENY))
        );

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

    private static StatementService.StatementInfo statement(StatementService.Effect effect) {
        return statement(effect, "true");
    }

    private static StatementService.StatementInfo statement(StatementService.Effect effect, String condition) {
        return new StatementService.StatementInfo(
            UUID.randomUUID(),
            "statement-" + UUID.randomUUID(),
            null,
            effect,
            new StatementService.TargetInfo(
                StatementService.TargetType.OBJECT,
                new StatementService.ApiInfo("GET", "/api/v0/objects")
            ),
            List.of(condition)
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

        @Override
        public void validate(AuthorizationCompiler.Expression predicate) {}
    }
}
