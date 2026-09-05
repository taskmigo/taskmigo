package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyEvaluator;
import io.taskmigo.auth.authorization.policy.PolicyIr;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.user.UserException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/// Evaluates request-targeted authorization Statements independently of the web security framework.
@Service
public class RequestAuthorizationService {

    private final EffectiveStatementResolver statements;
    private final JavaScriptPolicyEvaluator policyEvaluator;
    private final StatementArtifactFactory artifacts;

    RequestAuthorizationService(
        EffectiveStatementResolver statements,
        JavaScriptPolicyEvaluator policyEvaluator,
        StatementArtifactFactory artifacts
    ) {
        this.statements = statements;
        this.policyEvaluator = policyEvaluator;
        this.artifacts = artifacts;
    }

    /// Creates the one authorization snapshot used by a request operation.
    ///
    /// @param userId the user whose effective authorization state is captured
    /// @param roots the approved principal and request values for the operation
    /// @return an immutable authorization snapshot
    public AuthorizationSnapshot snapshot(UUID userId, Map<String, ?> roots) {
        List<StatementInfo> effectiveStatements = this.statements.resolve(userId);
        return new AuthorizationSnapshot(userId, effectiveStatements, this.artifacts.build(effectiveStatements), roots);
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
    public RequestAuthorizationDecision authorize(UUID userId, String method, String path, Map<String, ?> roots) {
        try {
            return this.authorize(this.snapshot(userId, roots), method, path);
        } catch (AuthorizationException | UserException exception) {
            return new RequestAuthorizationDecision(false);
        }
    }

    /// Evaluates a request using an already established authorization snapshot.
    ///
    /// @param snapshot the immutable authorization state for this operation
    /// @param method the HTTP method of the request
    /// @param path the request path without a query string
    /// @return the transport-neutral authorization decision
    public RequestAuthorizationDecision authorize(AuthorizationSnapshot snapshot, String method, String path) {
        Map<String, ?> approvedRoots = snapshot.roots();
        List<Evaluation> evaluations = new ArrayList<>();
        for (var artifact : snapshot.executableStatements()) {
            StatementInfo statement = artifact.statement();
            if (statement.scope() == Scope.REQUEST && artifact.matches(method, path)) {
                try {
                    if (statement.effect() == Effect.DENY && constantTrue(artifact.policy())) {
                        return new RequestAuthorizationDecision(false);
                    }
                    evaluations.add(new Evaluation(statement, artifact.policy()));
                } catch (AuthorizationException exception) {
                    return new RequestAuthorizationDecision(false);
                }
            }
        }

        boolean allowed = false;
        for (Evaluation evaluation : evaluations) {
            StatementInfo statement = evaluation.statement();
            try {
                boolean matches = this.policyEvaluator.evaluate(evaluation.policy(), approvedRoots);
                if (matches) {
                    if (statement.effect() == Effect.DENY) {
                        return new RequestAuthorizationDecision(false);
                    }
                    allowed = true;
                }
            } catch (AuthorizationException exception) {
                return new RequestAuthorizationDecision(false);
            }
        }
        return new RequestAuthorizationDecision(allowed);
    }

    private static boolean constantTrue(PolicyIr policy) {
        return policy.expression() instanceof PolicyIr.Literal literal && Boolean.TRUE.equals(literal.value());
    }

    private record Evaluation(StatementInfo statement, PolicyIr policy) {}
}
