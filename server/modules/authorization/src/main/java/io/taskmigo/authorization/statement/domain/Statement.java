package io.taskmigo.authorization.statement.domain;

import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Owns canonical Statement identity and structurally valid mutable policy state.
public final class Statement {

    private final UUID id;
    private final StatementCode code;
    @Nullable
    private String description;
    private Effect effect;
    private Scope scope;
    private StatementTarget target;
    private StatementPolicy policy;

    private Statement(
        UUID id,
        StatementCode code,
        @Nullable String description,
        Effect effect,
        Scope scope,
        StatementTarget target,
        StatementPolicy policy
    ) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.description = description;
        this.effect = Objects.requireNonNull(effect);
        this.scope = Objects.requireNonNull(scope);
        this.target = Objects.requireNonNull(target);
        this.policy = Objects.requireNonNull(policy);
    }

    /// Creates a new Statement using structural invariants only.
    public static Statement create(
        UUID id,
        @Nullable String code,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        return new Statement(
            id,
            StatementCode.of(code),
            description,
            required(effect, "effect"),
            required(scope, "scope"),
            StatementTarget.of(method, path),
            StatementPolicy.of(policy)
        );
    }

    /// Reconstitutes canonical persisted Statement state without persistence revision metadata.
    public static Statement restore(
        UUID id,
        String code,
        @Nullable String description,
        Effect effect,
        Scope scope,
        String method,
        String path,
        String policy
    ) {
        return create(id, code, description, effect, scope, method, path, policy);
    }

    public UUID id() {
        return this.id;
    }

    public StatementCode code() {
        return this.code;
    }

    public @Nullable String description() {
        return this.description;
    }

    public Effect effect() {
        return this.effect;
    }

    public Scope scope() {
        return this.scope;
    }

    public StatementTarget target() {
        return this.target;
    }

    public StatementPolicy policy() {
        return this.policy;
    }

    /// Reconciles managed mutable state while preserving the stable Statement id and code.
    public boolean reconcile(
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    ) {
        Effect requestedEffect = required(effect, "effect");
        Scope requestedScope = required(scope, "scope");
        StatementTarget requestedTarget = StatementTarget.of(method, path);
        StatementPolicy requestedPolicy = StatementPolicy.of(policy);
        if (
            Objects.equals(this.description, description) &&
            this.effect == requestedEffect &&
            this.scope == requestedScope &&
            this.target.equals(requestedTarget) &&
            this.policy.equals(requestedPolicy)
        ) {
            return false;
        }

        this.description = description;
        this.effect = requestedEffect;
        this.scope = requestedScope;
        this.target = requestedTarget;
        this.policy = requestedPolicy;
        return true;
    }

    private static <T> T required(@Nullable T value, String field) {
        if (value == null) {
            throw StatementRuleViolation.required(field);
        }
        return value;
    }
}
