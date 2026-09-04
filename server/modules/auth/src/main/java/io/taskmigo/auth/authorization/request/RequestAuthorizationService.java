package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyEvaluator;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Evaluates request-targeted authorization Statements independently of the web security framework.
@Service
public class RequestAuthorizationService {

    private final EffectiveStatementResolver statements;
    private final JavaScriptPolicyCompiler policyCompiler;
    private final JavaScriptPolicyEvaluator policyEvaluator;

    RequestAuthorizationService(
        EffectiveStatementResolver statements,
        JavaScriptPolicyCompiler policyCompiler,
        JavaScriptPolicyEvaluator policyEvaluator
    ) {
        this.statements = statements;
        this.policyCompiler = policyCompiler;
        this.policyEvaluator = policyEvaluator;
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
    @Transactional(readOnly = true)
    public RequestAuthorizationDecision authorize(UUID userId, String method, String path, Map<String, ?> roots) {
        boolean allowed = false;
        for (StatementInfo statement : this.statements.resolve(userId)) {
            if (statement.scope() == Scope.REQUEST && statement.matches(method, path)) {
                try {
                    boolean matches = statement.policy() == null || this.policyEvaluator.evaluate(
                        this.policyCompiler.compile(statement.policy(), Scope.REQUEST),
                        roots
                    );
                    if (matches) {
                        if (statement.effect() == Effect.DENY) return new RequestAuthorizationDecision(false);
                        allowed = true;
                    }
                } catch (RuntimeException exception) {
                    return new RequestAuthorizationDecision(false);
                }
            }
        }
        return new RequestAuthorizationDecision(allowed);
    }
}
