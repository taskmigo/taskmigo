package io.taskmigo.authorization.request.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.request.domain.RequestAuthorizationDecider.Decision;
import io.taskmigo.authorization.request.domain.RequestAuthorizationDecider.Evaluation;
import io.taskmigo.authorization.request.domain.RequestAuthorizationDecider.Rule;
import io.taskmigo.authorization.request.domain.RequestAuthorizationDecider.State;
import io.taskmigo.authorization.statement.Effect;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestAuthorizationDeciderTest {

    private final RequestAuthorizationDecider decider = new RequestAuthorizationDecider();

    /**
     * Verifies that Request Authorization defaults to deny when no rule grants access.
     *
     * Given: an authorization operation with no target-matching rules.
     * Expect: finalizing the initial state produces a denied decision.
     */
    @Test
    @DisplayName("defaults to deny when no request rule matches")
    void shouldDenyWhenNoRequestRuleMatches() {
        // Arrange
        State state = this.decider.start(List.of());

        // Act
        Decision result = this.decider.finish(state);

        // Assert
        assertThat(result).isEqualTo(Decision.DENY);
    }

    /**
     * Verifies that one matching allow rule grants access in the absence of a matching deny.
     *
     * Given: one ALLOW rule whose normalized policy evaluation matches.
     * Expect: the state becomes provisionally allowed and finalizes to ALLOW.
     */
    @Test
    @DisplayName("allows when a request allow rule matches")
    void shouldAllowWhenRequestAllowRuleMatches() {
        // Arrange
        State state = this.decider.start(List.of(new Rule(Effect.ALLOW, false)));

        // Act
        state = this.decider.apply(state, Effect.ALLOW, Evaluation.MATCHES);
        Decision result = this.decider.finish(state);

        // Assert
        assertThat(result).isEqualTo(Decision.ALLOW);
    }

    /**
     * Verifies deny-overrides semantics independently of policy execution infrastructure.
     *
     * Given: a matching ALLOW followed by a matching DENY for the same authorization operation.
     * Expect: the later deny changes provisional allow state into a final denied decision.
     */
    @Test
    @DisplayName("denies when a request deny rule matches alongside an allow")
    void shouldDenyWhenRequestDenyRuleMatchesAlongsideAllow() {
        // Arrange
        State state = this.decider.start(List.of(new Rule(Effect.ALLOW, false), new Rule(Effect.DENY, false)));

        // Act
        state = this.decider.apply(state, Effect.ALLOW, Evaluation.MATCHES);
        state = this.decider.apply(state, Effect.DENY, Evaluation.MATCHES);
        Decision result = this.decider.finish(state);

        // Assert
        assertThat(result).isEqualTo(Decision.DENY);
        assertThat(state.terminal()).isTrue();
    }

    /**
     * Verifies fail-closed semantics after the application layer normalizes a policy execution failure.
     *
     * Given: one ALLOW rule whose policy evaluation is reported as ERROR.
     * Expect: the state becomes conclusively denied.
     */
    @Test
    @DisplayName("fails closed when a request policy evaluation reports an error")
    void shouldDenyWhenRequestPolicyEvaluationReportsError() {
        // Arrange
        State state = this.decider.start(List.of(new Rule(Effect.ALLOW, false)));

        // Act
        state = this.decider.apply(state, Effect.ALLOW, Evaluation.ERROR);
        Decision result = this.decider.finish(state);

        // Assert
        assertThat(result).isEqualTo(Decision.DENY);
        assertThat(state.terminal()).isTrue();
    }

    /**
     * Verifies a constant-true deny is terminal before application-level policy evaluation starts.
     *
     * Given: an ALLOW rule and a constant-true DENY rule in the same operation.
     * Expect: initial decision state is already terminal and finalizes to DENY.
     */
    @Test
    @DisplayName("short-circuits before evaluation when a constant request deny exists")
    void shouldStartDeniedWhenConstantRequestDenyExists() {
        // Arrange
        List<Rule> rules = List.of(new Rule(Effect.ALLOW, false), new Rule(Effect.DENY, true));

        // Act
        State state = this.decider.start(rules);
        Decision result = this.decider.finish(state);

        // Assert
        assertThat(state.terminal()).isTrue();
        assertThat(result).isEqualTo(Decision.DENY);
    }
}
