package io.taskmigo.authorization.request.domain;

import io.taskmigo.authorization.statement.Effect;
import java.util.List;
import java.util.Objects;

/// Applies the Request Authorization truth table without depending on persistence, Spring, Language, or published APIs.
public final class RequestAuthorizationDecider {

    /// Establishes the initial decision state after applying constant-deny semantics.
    ///
    /// @param rules target-matching rule metadata for one immutable authorization operation
    /// @return DENIED when a constant-true deny exists; otherwise an undecided state
    public State start(List<Rule> rules) {
        for (Rule rule : List.copyOf(rules)) {
            if (rule.effect() == Effect.DENY && rule.constantTrue()) {
                return State.DENIED;
            }
        }
        return State.UNDECIDED;
    }

    /// Advances pure decision state from one normalized policy evaluation.
    ///
    /// Evaluation errors fail closed, a matching deny is final, and a matching allow remains provisional because a
    /// later deny can override it.
    ///
    /// @param state the decision state produced by preceding rules
    /// @param effect the effect of the rule that was evaluated
    /// @param evaluation the normalized policy evaluation outcome
    /// @return the next decision state
    public State apply(State state, Effect effect, Evaluation evaluation) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(effect);
        Objects.requireNonNull(evaluation);
        if (state == State.DENIED) {
            return State.DENIED;
        }
        return switch (evaluation) {
            case ERROR -> State.DENIED;
            case DOES_NOT_MATCH -> state;
            case MATCHES -> effect == Effect.DENY ? State.DENIED : State.ALLOWED;
        };
    }

    /// Finalizes the default-deny truth table after every required policy evaluation has been incorporated.
    ///
    /// @param state the final accumulated decision state
    /// @return ALLOW only when at least one allow matched and no deny or error occurred
    public Decision finish(State state) {
        return Objects.requireNonNull(state) == State.ALLOWED ? Decision.ALLOW : Decision.DENY;
    }

    /// Represents the domain-level outcome before the application layer maps it to a published result.
    public enum Decision {
        ALLOW,
        DENY;

        public boolean allowed() {
            return this == ALLOW;
        }
    }

    /// Tracks whether authorization is still undecided, provisionally allowed, or conclusively denied.
    public enum State {
        UNDECIDED,
        ALLOWED,
        DENIED;

        public boolean terminal() {
            return this == DENIED;
        }
    }

    /// Describes target-matching rule metadata needed before application-level policy evaluation.
    public record Rule(Effect effect, boolean constantTrue) {
        public Rule {
            Objects.requireNonNull(effect);
        }
    }

    /// Normalizes application-layer policy evaluation into the values needed by the Request decision truth table.
    public enum Evaluation {
        MATCHES,
        DOES_NOT_MATCH,
        ERROR,
    }
}
