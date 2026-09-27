package io.taskmigo.authorization.statement;

import io.taskmigo.language.CompiledSource;

/// Holds the executable derivatives of one database-loaded Statement for one authorization operation.
public record StatementExecutionArtifact(StatementInfo statement, CompiledSource policy, StatementTargetPattern pathMatcher) {
    /// Tests the request target using the matcher prepared when the operation snapshot was built.
    public boolean matches(String requestMethod, String requestPath) {
        return (
            (this.statement.target().api().method().equals("*") ||
                this.statement.target().api().method().equals(requestMethod)) &&
            this.pathMatcher.matches(requestPath)
        );
    }
}
