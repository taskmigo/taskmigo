package io.taskmigo.auth.authorization.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.filter.FilterAst;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.policy.PolicyIrPartialEvaluator;
import io.taskmigo.auth.authorization.request.AuthorizationSnapshot;
import io.taskmigo.auth.authorization.request.EffectiveStatementResolver;
import io.taskmigo.auth.authorization.statement.ApiInfo;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.TargetInfo;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObjectAuthorizationServiceTest {

    private final EffectiveStatementResolver statements = mock(EffectiveStatementResolver.class);
    private final AuthorizationObjectQueryDialect dialect = new TestDialect();
    private final ObjectAuthorizationService service = new ObjectAuthorizationService(
        this.statements,
        new JavaScriptPolicyCompiler(),
        new PolicyIrPartialEvaluator(),
        List.of(this.dialect)
    );

    /**
     * Verifies that an unconditional object Statement contributes an allow predicate to the object plan.
     *
     * Given: an unconditional allow Statement targeting the queried object operation.
     * Expect: the plan contains one matching Statement and a true allow branch.
     */
    @Test
    @DisplayName("builds an unconditional object allow filter")
    void shouldBuildUnconditionalAllowFilterWhenStatementHasNoPolicy() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(this.statements.resolve(userId)).thenReturn(List.of(statement(Effect.ALLOW, null)));

        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(
            userId,
            "GET",
            "/api/v0/objects",
            roots(userId.toString(), "GET")
        );

        // Assert
        assertThat(plan.matchedStatements()).hasSize(1);
        assertThat(plan.predicate().expression()).isEqualTo(
            new FilterAst.Binary(
                FilterAst.Operator.AND,
                new FilterAst.All(),
                new FilterAst.Unary(FilterAst.Operator.NOT, new FilterAst.None())
            )
        );
    }

    /**
     * Verifies that an object deny predicate overrides an otherwise matching allow predicate.
     *
     * Given: unconditional allow and deny Statements targeting the same object operation.
     * Expect: the combined filter contains an allow-any branch and a negated deny-any branch.
     */
    @Test
    @DisplayName("applies deny override to object filters")
    void shouldDenyObjectWhenMatchingDenyStatementExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(this.statements.resolve(userId)).thenReturn(
            List.of(statement(Effect.ALLOW, null), statement(Effect.DENY, null))
        );

        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(
            userId,
            "GET",
            "/api/v0/objects",
            roots(userId.toString(), "GET")
        );

        // Assert
        assertThat(plan.predicate().expression()).isEqualTo(
            new FilterAst.Binary(
                FilterAst.Operator.AND,
                new FilterAst.All(),
                new FilterAst.Unary(FilterAst.Operator.NOT, new FilterAst.All())
            )
        );
    }

    /**
     * Verifies that known principal and request values are specialized while object values remain database fields.
     *
     * Given: an Object policy comparing an object username with the known principal username.
     * Expect: the plan contains an equality between the mapped object field and the principal literal.
     */
    @Test
    @DisplayName("specializes known roots in object policy filters")
    void shouldSpecializeKnownRootsWhenBuildingObjectPolicyFilter() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String policy = "export default ({ object, principal }) => object.username === principal.username;";
        when(this.statements.resolve(userId)).thenReturn(List.of(statement(Effect.ALLOW, policy)));

        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(
            userId,
            "GET",
            "/api/v0/objects",
            Map.of("principal", Map.of("username", "alice"))
        );

        // Assert
        assertThat(plan.predicate().expression()).isEqualTo(
            new FilterAst.Binary(
                FilterAst.Operator.AND,
                new FilterAst.Binary(
                    FilterAst.Operator.EQ,
                    new FilterAst.Field("username"),
                    new FilterAst.Literal("alice")
                ),
                new FilterAst.Unary(FilterAst.Operator.NOT, new FilterAst.None())
            )
        );
    }

    /**
     * Verifies that an object policy checking a field's presence becomes a database presence filter.
     *
     * Given: an Object policy comparing a mapped field with JavaScript undefined.
     * Expect: the residual filter uses the Filter AST presence operator rather than a JVM row check.
     */
    @Test
    @DisplayName("translates undefined object checks to presence filters")
    void shouldTranslateUndefinedWhenBuildingObjectPolicyFilter() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String policy = "export default ({ object }) => object.username !== undefined;";
        when(this.statements.resolve(userId)).thenReturn(List.of(statement(Effect.ALLOW, policy)));

        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(
            userId,
            "GET",
            "/api/v0/objects",
            Map.of()
        );

        // Assert
        assertThat(plan.predicate().expression()).isEqualTo(
            new FilterAst.Binary(
                FilterAst.Operator.AND,
                new FilterAst.Unary(FilterAst.Operator.PRESENT, new FilterAst.Field("username")),
                new FilterAst.Unary(FilterAst.Operator.NOT, new FilterAst.None())
            )
        );
    }

    /**
     * Verifies that an object expression without a supported database representation is rejected.
     *
     * Given: an Object policy reading a nested computed property from an object field.
     * Expect: planning fails closed instead of evaluating each returned row in the JVM.
     */
    @Test
    @DisplayName("rejects non-translatable object policies")
    void shouldRejectPolicyWhenObjectExpressionCannotBecomeFilter() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String policy = "export default ({ object }) => object.username.length > 2;";
        when(this.statements.resolve(userId)).thenReturn(List.of(statement(Effect.ALLOW, policy)));

        // Act + Assert
        assertThatThrownBy(() -> this.service.plan(userId, "GET", "/api/v0/objects", Map.of()))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("queryable");
    }

    /**
     * Verifies that object planning consumes effective Statements from the supplied snapshot.
     *
     * Given: an allow snapshot and a resolver with no configured follow-up result.
     * Expect: the object plan contains the snapshot Statement without resolving authorization state again.
     */
    @Test
    @DisplayName("reuses the authorization snapshot during object planning")
    void shouldReuseAuthorizationSnapshotWhenBuildingObjectPlan() {
        // Arrange
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = new AuthorizationSnapshot(
            userId,
            List.of(statement(Effect.ALLOW, null)),
            roots(userId.toString(), "GET")
        );

        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(snapshot, "GET", "/api/v0/objects");

        // Assert
        assertThat(plan.matchedStatements()).hasSize(1);
        verifyNoInteractions(this.statements);
    }

    private static StatementInfo statement(Effect effect, @Nullable String policy) {
        return new StatementInfo(
            UUID.randomUUID(),
            "statement-" + UUID.randomUUID(),
            null,
            effect,
            Scope.OBJECT,
            new TargetInfo(new ApiInfo("GET", "/api/v0/objects")),
            policy
        );
    }

    private static Map<String, ?> roots(String username, String method) {
        return Map.of("principal", Map.of("username", username), "request", Map.of("method", method));
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
            return Map.of("username", String.class);
        }
    }
}
