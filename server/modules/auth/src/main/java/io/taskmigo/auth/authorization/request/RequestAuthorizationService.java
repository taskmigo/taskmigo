package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.condition.AuthorizationCompiler;
import io.taskmigo.auth.authorization.condition.AuthorizationExpressionEvaluator;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.TargetType;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Evaluates request-targeted authorization Statements independently of the web security framework.
@Service
public class RequestAuthorizationService {

    private final EffectiveStatementResolver statements;
    private final AuthorizationCompiler compiler;
    private final AuthorizationExpressionEvaluator evaluator;

    RequestAuthorizationService(
        EffectiveStatementResolver statements,
        AuthorizationCompiler compiler,
        AuthorizationExpressionEvaluator evaluator
    ) {
        this.statements = statements;
        this.compiler = compiler;
        this.evaluator = evaluator;
    }

    /// Returns whether a user is allowed to perform an HTTP request.
    ///
    /// Matching allow Statements grant access, while a matching deny Statement always overrides an allow. Conditions
    /// are evaluated against the supplied authorization roots.
    ///
    /// @param userId the user whose effective Statements are evaluated
    /// @param method the HTTP method of the request
    /// @param path the request path without a query string
    /// @param roots the principal and request values exposed to conditions
    /// @return the transport-neutral authorization decision
    @Transactional(readOnly = true)
    public RequestAuthorizationDecision authorize(UUID userId, String method, String path, Map<String, ?> roots) {
        boolean allowed = false;
        for (StatementInfo statement : this.statements.resolve(userId)) {
            if (
                statement.target().type() == TargetType.REQUEST &&
                statement.matches(method, path) &&
                this.evaluator.evaluate(this.compiler.compile(statement), roots)
            ) {
                if (statement.effect() == Effect.DENY) return new RequestAuthorizationDecision(false);
                allowed = true;
            }
        }
        return new RequestAuthorizationDecision(allowed);
    }
}
