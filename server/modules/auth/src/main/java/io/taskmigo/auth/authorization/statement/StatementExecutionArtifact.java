package io.taskmigo.auth.authorization.statement;

import io.taskmigo.auth.authorization.policy.PolicyIr;
import java.util.regex.Pattern;

/// Holds the executable derivatives of one database-loaded Statement for one authorization operation.
public record StatementExecutionArtifact(StatementInfo statement, PolicyIr policy, Pattern pathMatcher) {
    /// Tests the request target using the matcher prepared when the operation snapshot was built.
    public boolean matches(String requestMethod, String requestPath) {
        String pathWithoutQuery = requestPath.split("\\?", 2)[0];
        return (
            (this.statement.target().api().method().equals("*") ||
                this.statement.target().api().method().equals(requestMethod)) &&
            this.pathMatcher.matcher(pathWithoutQuery).matches()
        );
    }
}
