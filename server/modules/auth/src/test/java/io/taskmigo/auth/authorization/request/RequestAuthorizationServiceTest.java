package io.taskmigo.auth.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.auth.authorization.policy.AuthorizationResourceAdapter;
import io.taskmigo.auth.authorization.policy.AuthorizationResourceRegistry;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyEvaluator;
import io.taskmigo.auth.authorization.statement.ApiInfo;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.TargetInfo;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestAuthorizationServiceTest {

    private final EffectiveStatementResolver statements = mock(EffectiveStatementResolver.class);
    private final RequestAuthorizationService service = new RequestAuthorizationService(
        this.statements,
        new JavaScriptPolicyCompiler(),
        new JavaScriptPolicyEvaluator(),
        new AuthorizationResourceRegistry(new JavaScriptPolicyEvaluator(), List.of())
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

    /**
     * Verifies that an allow Statement only grants access when its JavaScript policy returns true.
     *
     * Given: a matching allow Statement whose policy requires the request method to be GET.
     * Expect: GET is allowed and POST is denied by the policy IR evaluator.
     */
    @Test
    @DisplayName("evaluates a request policy before allowing access")
    void shouldEvaluateRequestPolicyWhenMatchingAllowStatementExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(this.statements.resolve(userId)).thenReturn(List.of(statement(
            Effect.ALLOW,
            "export default ({ request }) => request.method === 'GET';"
        )));

        // Act
        RequestAuthorizationDecision getResult = this.service.authorize(
            userId,
            "GET",
            "/api/v0/users",
            Map.of("request", Map.of("method", "GET"))
        );
        RequestAuthorizationDecision postResult = this.service.authorize(
            userId,
            "POST",
            "/api/v0/users",
            Map.of("request", Map.of("method", "POST"))
        );

        // Assert
        assertThat(getResult.allowed()).isTrue();
        assertThat(postResult.allowed()).isFalse();
    }

    /**
     * Verifies that a matching deny policy overrides an allow policy only when the deny policy returns true.
     *
     * Given: matching allow and deny Statements where the deny policy checks a principal flag.
     * Expect: the flagged principal is denied and the unflagged principal remains allowed.
     */
    @Test
    @DisplayName("applies deny override after evaluating request policies")
    void shouldApplyDenyOverrideWhenDenyPolicyReturnsTrue() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(this.statements.resolve(userId)).thenReturn(List.of(
            statement(Effect.ALLOW, "export default ({ request }) => request.method === 'GET';"),
            statement(Effect.DENY, "export default ({ principal }) => principal.blocked === true;")
        ));

        // Act
        RequestAuthorizationDecision blocked = this.service.authorize(
            userId,
            "GET",
            "/api/v0/users",
            Map.of("request", Map.of("method", "GET"), "principal", Map.of("blocked", true))
        );
        RequestAuthorizationDecision unblocked = this.service.authorize(
            userId,
            "GET",
            "/api/v0/users",
            Map.of("request", Map.of("method", "GET"), "principal", Map.of("blocked", false))
        );

        // Assert
        assertThat(blocked.allowed()).isFalse();
        assertThat(unblocked.allowed()).isTrue();
    }

    /**
     * Verifies that a policy evaluation failure cannot turn into an authorization grant.
     *
     * Given: a matching allow Statement containing malformed JavaScript policy source.
     * Expect: authorization returns a denied decision.
     */
    @Test
    @DisplayName("fails closed when request policy evaluation fails")
    void shouldDenyRequestWhenPolicyEvaluationFails() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(this.statements.resolve(userId)).thenReturn(List.of(statement(
            Effect.ALLOW,
            "export default ({ request }) => request.method === ;"
        )));

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

    /**
     * Verifies that a request policy is evaluated against explicitly selected persisted resource values.
     *
     * Given: a request policy selecting a user by path variable and a batched adapter returning that user.
     * Expect: the policy can read the resolved user while the adapter is called only once.
     */
    @Test
    @DisplayName("evaluates request policy against resolved resources")
    void shouldEvaluateResolvedResourceWhenRequestPolicySelectsUser() {
        // Arrange
        UUID userId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        AuthorizationResourceAdapter users = new AuthorizationResourceAdapter() {
            @Override
            public String type() {
                return "user";
            }

            @Override
            public Map<String, Map<String, ?>> resolve(Collection<String> keys) {
                calls.incrementAndGet();
                assertThat(keys).containsExactly("target-user");
                return Map.of("target-user", Map.of("username", "alice"));
            }
        };
        JavaScriptPolicyEvaluator evaluator = new JavaScriptPolicyEvaluator();
        RequestAuthorizationService service = new RequestAuthorizationService(
            this.statements,
            new JavaScriptPolicyCompiler(),
            evaluator,
            new AuthorizationResourceRegistry(evaluator, List.of(users))
        );
        when(this.statements.resolve(userId)).thenReturn(List.of(statement(
            Effect.ALLOW,
            """
            export function resources({ request }) {
              return { user: resource("user", request.path.userId) };
            }
            export default ({ object }) => object.user.username === "alice";
            """
        )));

        // Act
        RequestAuthorizationDecision result = service.authorize(
            userId,
            "GET",
            "/api/v0/users",
            Map.of("request", Map.of("path", Map.of("userId", "target-user")))
        );

        // Assert
        assertThat(result.allowed()).isTrue();
        assertThat(calls).hasValue(1);
    }

    private static StatementInfo statement(Effect effect) {
        return statement(effect, null);
    }

    private static StatementInfo statement(Effect effect, @Nullable String policy) {
        return new StatementInfo(
            UUID.randomUUID(),
            "statement-" + UUID.randomUUID(),
            null,
            effect,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", "/api/v0/users")),
            policy
        );
    }
}
