package io.taskmigo.auth.authorization.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.filter.FilterAst;
import io.taskmigo.auth.authorization.policy.PolicyIrPartialEvaluator;
import io.taskmigo.auth.authorization.request.AuthorizationSnapshot;
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

    private final AuthorizationObjectQueryDialect dialect = new TestDialect();
    private final ObjectAuthorizationService service = new ObjectAuthorizationService(
        new PolicyIrPartialEvaluator(),
        List.of(this.dialect)
    );

    /**
     * Verifies that a constant-true object Statement contributes an allow predicate to the object plan.
     *
     * Given: an allow Statement with `export default () => true;` targeting the queried object operation.
     * Expect: the plan contains one matching Statement and a true allow branch.
     */
    @Test
    @DisplayName("builds an unconditional object allow filter")
    void shouldBuildConstantTrueAllowFilterWhenStatementPolicyIsTrue() {
        // Arrange
        UUID userId = UUID.randomUUID();
        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(
            snapshot(userId, Map.of(), statement(Effect.ALLOW, "export default () => true;")),
            "GET",
            "/api/v0/objects"
        );

        // Assert
        assertThat(plan.matchedStatements()).hasSize(1);
        assertThat(plan.predicate().expression()).isEqualTo(new FilterAst.All());
    }

    /**
     * Verifies that an Object policy whose final value is not boolean is rejected during planning.
     *
     * Given: an allow Statement returning a numeric literal.
     * Expect: object authorization fails before a non-predicate plan can reach a repository query.
     */
    @Test
    @DisplayName("rejects a non-boolean object decision")
    void shouldRejectNonBooleanWhenBuildingObjectPolicyFilter() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act + Assert
        assertThatThrownBy(() ->
            this.service.plan(
                snapshot(userId, Map.of(), statement(Effect.ALLOW, "export default () => 1;")),
                "GET",
                "/api/v0/objects"
            )
        )
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("boolean");
    }

    /**
     * Verifies that an object deny predicate overrides an otherwise matching allow predicate.
     *
     * Given: constant-true allow and deny Statements targeting the same object operation.
     * Expect: the combined filter contains an allow-any branch and a negated deny-any branch.
     */
    @Test
    @DisplayName("applies deny override to object filters")
    void shouldDenyObjectWhenMatchingDenyStatementExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(
            snapshot(
                userId,
                Map.of(),
                statement(Effect.ALLOW, "export default () => true;"),
                statement(Effect.DENY, "export default () => true;")
            ),
            "GET",
            "/api/v0/objects"
        );

        // Assert
        assertThat(plan.predicate().expression()).isEqualTo(new FilterAst.None());
    }

    /**
     * Verifies that a constant deny prevents later non-queryable Object policies from being translated.
     *
     * Given: a constant-true deny followed by an Object policy with an unsupported undefined residual.
     * Expect: the plan is `NONE` and planning does not fail on the later policy.
     */
    @Test
    @DisplayName("short-circuits object planning on a constant deny")
    void shouldReturnNoneWhenObjectDenyPolicyIsConstantTrue() {
        // Arrange
        UUID userId = UUID.randomUUID();
        AuthorizationSnapshot snapshot = snapshot(
            userId,
            Map.of(),
            statement(Effect.DENY, "export default () => true;"),
            statement(Effect.ALLOW, "export default ({ object }) => object.username !== undefined;")
        );

        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(snapshot, "GET", "/api/v0/objects");

        // Assert
        assertThat(plan.predicate().expression()).isEqualTo(new FilterAst.None());
    }

    /**
     * Verifies that modulo arithmetic cannot enter a database Object filter.
     *
     * Given: an Object policy using modulo against a mapped numeric field.
     * Expect: planning fails with an authorization error instead of falling back to JVM row filtering.
     */
    @Test
    @DisplayName("rejects modulo arithmetic in object policies")
    void shouldRejectModuloWhenObjectPolicyCannotBeTranslated() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String policy = "export default ({ object }) => object.age % 2 === 0;";

        // Act + Assert
        assertThatThrownBy(() ->
            this.service.plan(snapshot(userId, Map.of(), statement(Effect.ALLOW, policy)), "GET", "/api/v0/objects")
        )
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("modulo");
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
        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(
            snapshot(userId, Map.of("principal", Map.of("username", "alice")), statement(Effect.ALLOW, policy)),
            "GET",
            "/api/v0/objects"
        );

        // Assert
        assertThat(plan.predicate().expression()).isEqualTo(
            new FilterAst.Binary(FilterAst.Operator.EQ, new FilterAst.Field("username"), new FilterAst.Literal("alice"))
        );
    }

    /**
     * Verifies that an object policy with an undefined residual is rejected.
     *
     * Given: an Object policy comparing a mapped field with JavaScript undefined.
     * Expect: planning fails closed because the Filter AST has no presence operator.
     */
    @Test
    @DisplayName("rejects undefined object residuals")
    void shouldRejectUndefinedWhenBuildingObjectPolicyFilter() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String policy = "export default ({ object }) => object.username !== undefined;";
        // Act + Assert
        assertThatThrownBy(() ->
            this.service.plan(snapshot(userId, Map.of(), statement(Effect.ALLOW, policy)), "GET", "/api/v0/objects")
        )
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("undefined");
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
        // Act + Assert
        assertThatThrownBy(() ->
            this.service.plan(snapshot(userId, Map.of(), statement(Effect.ALLOW, policy)), "GET", "/api/v0/objects")
        )
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("queryable");
    }

    /**
     * Verifies that a nullable Object comparison becomes a database null predicate.
     *
     * Given: an Object policy comparing the mapped description field with JavaScript null.
     * Expect: the residual Filter AST keeps the null literal and equality operator for JPA translation.
     */
    @Test
    @DisplayName("translates null object comparisons")
    void shouldTranslateNullWhenBuildingObjectPolicyFilter() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String policy = "export default ({ object }) => object.description === null;";

        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(
            snapshot(userId, Map.of(), statement(Effect.ALLOW, policy)),
            "GET",
            "/api/v0/objects"
        );

        // Assert
        assertThat(plan.predicate().expression()).isEqualTo(
            new FilterAst.Binary(FilterAst.Operator.EQ, new FilterAst.Field("description"), new FilterAst.Literal(null))
        );
    }

    /**
     * Verifies that known values and numeric Object fields remain in a database-side arithmetic residual.
     *
     * Given: an Object policy requiring `object.age + 1` to exceed a known principal threshold.
     * Expect: the plan contains ADD and GT nodes and does not require per-row JVM evaluation.
     */
    @Test
    @DisplayName("translates numeric object arithmetic")
    void shouldTranslateArithmeticWhenObjectPolicyUsesNumericField() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String policy = "export default ({ object, principal }) => object.age + 1 > principal.minimum;";

        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(
            snapshot(userId, Map.of("principal", Map.of("minimum", 18)), statement(Effect.ALLOW, policy)),
            "GET",
            "/api/v0/objects"
        );

        // Assert
        assertThat(plan.predicate().expression()).isEqualTo(
            new FilterAst.Binary(
                FilterAst.Operator.GT,
                new FilterAst.Binary(FilterAst.Operator.ADD, new FilterAst.Field("age"), new FilterAst.Literal(1.0)),
                new FilterAst.Literal(18)
            )
        );
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
            List.of(statement(Effect.ALLOW, "export default () => true;")),
            Map.of()
        );

        // Act
        ObjectAuthorizationService.ObjectAuthorizationPlan plan = this.service.plan(snapshot, "GET", "/api/v0/objects");

        // Assert
        assertThat(plan.matchedStatements()).hasSize(1);
    }

    private static StatementInfo statement(Effect effect, String policy) {
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
            return Map.of("username", String.class, "description", String.class, "age", Integer.class);
        }
    }

    private static AuthorizationSnapshot snapshot(UUID userId, Map<String, ?> roots, StatementInfo... statements) {
        return new AuthorizationSnapshot(userId, List.of(statements), roots);
    }
}
