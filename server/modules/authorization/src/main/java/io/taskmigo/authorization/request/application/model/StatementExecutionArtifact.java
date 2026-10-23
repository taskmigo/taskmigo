package io.taskmigo.authorization.request.application.model;

import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.language.CompiledSource;

/// Holds the executable derivatives of one database-loaded Statement for one authorization operation.
public record StatementExecutionArtifact(
    StatementInfo statement,
    CompiledSource policy,
    StatementTargetPathMatcher pathMatcher
) {
    /// Tests the request target using the matcher prepared when the operation snapshot was built.
    public boolean matches(String requestMethod, String requestPath) {
        return (
            (this.statement.target().api().method().equals("*") ||
                this.statement.target().api().method().equals(requestMethod)) &&
            this.pathMatcher.matches(requestPath)
        );
    }
}
