package io.taskmigo.authorization.request.application.service;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.authorization.request.RequestAuthorizationResult;
import io.taskmigo.authorization.request.application.model.AuthorizationOperation;
import io.taskmigo.authorization.request.application.model.AuthorizationSnapshot;
import io.taskmigo.authorization.request.application.port.in.api.RequestAuthorization;
import io.taskmigo.authorization.request.application.port.out.EffectiveStatement;
import io.taskmigo.authorization.request.application.port.out.EffectiveStatementResolver;
import io.taskmigo.authorization.request.domain.RequestAuthorizationDecider;
import io.taskmigo.authorization.request.domain.RequestAuthorizationDecider.Evaluation;
import io.taskmigo.authorization.request.domain.RequestAuthorizationDecider.Rule;
import io.taskmigo.authorization.request.domain.RequestAuthorizationDecider.State;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.EmbeddedLanguageException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// Resolves one authorization operation and delegates Request decision semantics to the pure domain decider.
public final class RequestAuthorizationService implements RequestAuthorization {

    private static final RequestAuthorizationDecider DECIDER = new RequestAuthorizationDecider();

    private final EffectiveStatementResolver statements;
    private final StatementArtifactFactory artifacts;

    public RequestAuthorizationService(EffectiveStatementResolver statements, StatementArtifactFactory artifacts) {
        this.statements = statements;
        this.artifacts = artifacts;
    }

    /// Authorizes typed request inputs and returns the same operation context used by the decision.
    @Override
    public RequestAuthorizationResult authorize(AuthorizationPrincipal principal, AuthorizationRequest request) {
        Map<String, ?> roots = Map.of(
            "principal",
            Map.of("id", principal.id().toString(), "username", principal.username()),
            "request",
            Map.of("method", request.method(), "path", request.path(), "pathVariables", request.pathVariables())
        );
        try {
            AuthorizationSnapshot snapshot = this.snapshot(principal.id(), request.method(), request.path(), roots);
            AuthorizationOperation operation = new AuthorizationOperation(snapshot, request.method(), request.path());
            boolean granted = this.authorize(operation.snapshot(), operation.method(), operation.path()).allowed();
            return new RequestAuthorizationResult(granted, operation);
        } catch (AuthorizationException exception) {
            return new RequestAuthorizationResult(false, new FailedAuthorizationContext());
        }
    }

    /// Returns whether a user is allowed to perform an HTTP request.
    ///
    /// Matching allow Statements grant access, while a matching deny Statement always overrides an allow. A policy
    /// failure returns a denied decision.
    ///
    /// @param userId the user whose effective Statements are evaluated
    /// @param method the HTTP method of the request
    /// @param path the request path without a query string
    /// @param roots the principal and request values exposed to authorization policies
    /// @return the transport-neutral authorization decision
    RequestAuthorizationDecision authorize(UUID userId, String method, String path, Map<String, ?> roots) {
        try {
            return this.authorize(this.snapshot(userId, method, path, roots), method, path);
        } catch (AuthorizationException exception) {
            return new RequestAuthorizationDecision(false);
        }
    }

    /// Evaluates a request using an already established authorization snapshot.
    ///
    /// @param snapshot the immutable authorization state for this operation
    /// @param method the HTTP method of the request
    /// @param path the request path without a query string
    /// @return the transport-neutral authorization decision
    RequestAuthorizationDecision authorize(AuthorizationSnapshot snapshot, String method, String path) {
        Map<String, ?> approvedRoots = snapshot.roots();
        List<PolicyRule> rules = new ArrayList<>();
        for (var artifact : snapshot.executableStatements()) {
            StatementInfo statement = artifact.statement();
            if (statement.scope() == Scope.REQUEST && artifact.matches(method, path)) {
                rules.add(new PolicyRule(statement.effect(), artifact.policy()));
            }
        }

        State state = DECIDER.start(
            rules
                .stream()
                .map(rule -> new Rule(rule.effect(), constantTrue(rule.policy())))
                .toList()
        );
        for (PolicyRule rule : rules) {
            if (state.terminal()) {
                break;
            }
            state = DECIDER.apply(state, rule.effect(), evaluate(rule.policy(), approvedRoots));
        }
        return new RequestAuthorizationDecision(DECIDER.finish(state).allowed());
    }

    /// Creates the one authorization snapshot used by a request operation.
    ///
    /// @param userId the user whose effective authorization state is captured
    /// @param method the current request method used to select target-matching Statements
    /// @param path the current request path used to select target-matching Statements
    /// @param roots the approved principal and request values for the operation
    /// @return an immutable authorization snapshot
    AuthorizationSnapshot snapshot(UUID userId, String method, String path, Map<String, ?> roots) {
        List<EffectiveStatement> effectiveStatements = this.statements.resolve(userId);
        return new AuthorizationSnapshot(userId, this.artifacts.build(effectiveStatements, method, path), roots);
    }

    private static Evaluation evaluate(CompiledSource policy, Map<String, ?> roots) {
        try {
            Object value = policy.evaluate(roots);
            if (!(value instanceof Boolean matches)) {
                return Evaluation.ERROR;
            }
            return matches ? Evaluation.MATCHES : Evaluation.DOES_NOT_MATCH;
        } catch (AuthorizationException | EmbeddedLanguageException exception) {
            return Evaluation.ERROR;
        }
    }

    private static boolean constantTrue(CompiledSource policy) {
        return policy.constantBoolean().orElse(false);
    }

    private record PolicyRule(Effect effect, CompiledSource policy) {}

    private static final class FailedAuthorizationContext implements AuthorizationContext {}
}
