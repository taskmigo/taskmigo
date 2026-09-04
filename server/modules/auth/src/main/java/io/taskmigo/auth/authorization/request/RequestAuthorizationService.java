package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.condition.AuthorizationException;
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

    RequestAuthorizationService(EffectiveStatementResolver statements) {
        this.statements = statements;
    }

    /// Returns whether a user is allowed to perform an HTTP request.
    ///
    /// Matching unconditional allow Statements grant access, while a matching deny Statement always overrides an
    /// allow. JavaScript policies are activated in the subsequent authorization phase.
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
                if (statement.policy() != null) throw new AuthorizationException(
                    "Statement policy evaluation is not available yet"
                );
                if (statement.effect() == Effect.DENY) return new RequestAuthorizationDecision(false);
                allowed = true;
            }
        }
        return new RequestAuthorizationDecision(allowed);
    }
}
