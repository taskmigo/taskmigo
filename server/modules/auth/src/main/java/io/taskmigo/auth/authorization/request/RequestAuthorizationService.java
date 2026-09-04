package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.policy.AuthorizationResourceRegistry;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyEvaluator;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyModule;
import io.taskmigo.auth.authorization.policy.ResolvedResources;
import io.taskmigo.auth.authorization.policy.ResourceDescriptor;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Evaluates request-targeted authorization Statements independently of the web security framework.
@Service
public class RequestAuthorizationService {

    private final EffectiveStatementResolver statements;
    private final JavaScriptPolicyCompiler policyCompiler;
    private final JavaScriptPolicyEvaluator policyEvaluator;
    private final AuthorizationResourceRegistry resources;

    RequestAuthorizationService(
        EffectiveStatementResolver statements,
        JavaScriptPolicyCompiler policyCompiler,
        JavaScriptPolicyEvaluator policyEvaluator,
        AuthorizationResourceRegistry resources
    ) {
        this.statements = statements;
        this.policyCompiler = policyCompiler;
        this.policyEvaluator = policyEvaluator;
        this.resources = resources;
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
        Map<String, ?> approvedRoots = Map.copyOf(roots);
        List<Evaluation> evaluations = new ArrayList<>();
        List<ResourceDescriptor> descriptors = new ArrayList<>();
        for (StatementInfo statement : this.statements.resolve(userId)) {
            if (statement.scope() != Scope.REQUEST || !statement.matches(method, path)) continue;
            try {
                JavaScriptPolicyModule module = statement.policy() == null
                    ? null
                    : this.policyCompiler.compileModule(statement.policy(), Scope.REQUEST);
                if (module != null) descriptors.addAll(module.resources());
                evaluations.add(new Evaluation(statement, module));
            } catch (RuntimeException exception) {
                return new RequestAuthorizationDecision(false);
            }
        }

        ResolvedResources resolved;
        try {
            resolved = this.resources.resolve(descriptors, approvedRoots);
        } catch (RuntimeException exception) {
            return new RequestAuthorizationDecision(false);
        }

        boolean allowed = false;
        for (Evaluation evaluation : evaluations) {
            StatementInfo statement = evaluation.statement();
            try {
                Map<String, ?> evaluationRoots = approvedRoots;
                if (evaluation.module() != null) {
                    Map<String, Object> withObject = new LinkedHashMap<>(approvedRoots);
                    withObject.put("object", resolved.objectValues(evaluation.module().resources()));
                    evaluationRoots = Collections.unmodifiableMap(withObject);
                }
                boolean matches = evaluation.module() == null || this.policyEvaluator.evaluate(
                    evaluation.module().policy(),
                    evaluationRoots
                );
                if (matches) {
                    if (statement.effect() == Effect.DENY) return new RequestAuthorizationDecision(false);
                    allowed = true;
                }
            } catch (RuntimeException exception) {
                return new RequestAuthorizationDecision(false);
            }
        }
        return new RequestAuthorizationDecision(allowed);
    }

    private record Evaluation(StatementInfo statement, @Nullable JavaScriptPolicyModule module) {}
}
