package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.embeddedlanguage.AuthorizationEmbeddedLanguageSchemas;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.user.UserException;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageEvaluator;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageException;
import io.taskmigo.embeddedlanguage.SemanticAst;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/// Evaluates request-targeted authorization Statements independently of the web security framework.
@Service
@SuppressWarnings("checkstyle:OverloadMethodsDeclarationOrder")
public class RequestAuthorizationService implements RequestAuthorization {

    private final EffectiveStatementResolver statements;
    private final EmbeddedLanguageEvaluator embeddedLanguageEvaluator;
    private final StatementArtifactFactory artifacts;

    RequestAuthorizationService(
        EffectiveStatementResolver statements,
        EmbeddedLanguageEvaluator embeddedLanguageEvaluator,
        StatementArtifactFactory artifacts
    ) {
        this.statements = statements;
        this.embeddedLanguageEvaluator = embeddedLanguageEvaluator;
        this.artifacts = artifacts;
    }

    /// Authorizes typed request inputs and returns the same operation context used by the decision.
    @Override
    public RequestAuthorizationResult authorize(AuthorizationPrincipal principal, AuthorizationRequest request) {
        Map<String, ?> roots = Map.of(
            "principal", Map.of("id", principal.id().toString(), "username", principal.username()),
            "request", Map.of("method", request.method(), "path", request.path(), "pathVariables", request.pathVariables())
        );
        try {
            AuthorizationSnapshot snapshot = this.snapshot(principal.id(), roots);
            AuthorizationOperation operation = new AuthorizationOperation(snapshot, request.method(), request.path());
            boolean granted = this.authorize(operation.snapshot(), operation.method(), operation.path()).allowed();
            return new RequestAuthorizationResult(granted, operation);
        } catch (AuthorizationException | UserException exception) {
            return new RequestAuthorizationResult(false, new FailedAuthorizationContext());
        }
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
                if (statement.effect() == Effect.DENY && constantTrue(artifact.policy())) {
                    return new RequestAuthorizationDecision(false);
                }
                evaluations.add(new Evaluation(statement, artifact.policy()));
            }
        }

        boolean allowed = false;
        for (Evaluation evaluation : evaluations) {
            StatementInfo statement = evaluation.statement();
            try {
                @Nullable Object value = this.embeddedLanguageEvaluator.evaluate(
                    evaluation.policy(),
                    AuthorizationEmbeddedLanguageSchemas.request(),
                    approvedRoots
                );
                if (!(value instanceof Boolean matches)) {
                    throw new AuthorizationException("Request authorization policy result is not Bool");
                }
                if (matches) {
                    if (statement.effect() == Effect.DENY) {
                        return new RequestAuthorizationDecision(false);
                    }
                    allowed = true;
                }
            } catch (AuthorizationException | EmbeddedLanguageException exception) {
                return new RequestAuthorizationDecision(false);
            }
        }
        return new RequestAuthorizationDecision(allowed);
    }

    private static boolean constantTrue(SemanticAst policy) {
        return policy.expression() instanceof SemanticAst.Literal literal && Boolean.TRUE.equals(literal.value());
    }

    private record Evaluation(StatementInfo statement, SemanticAst policy) {}

    private static final class FailedAuthorizationContext implements AuthorizationContext {}
}
