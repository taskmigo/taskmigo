package io.taskmigo.authorization.statement;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import io.taskmigo.authorization.core.AuthorizationException;

/// Compiles and applies the bounded regular-expression contract for Statement API target paths.
///
/// Target paths use RE2-compatible syntax and are evaluated by RE2/J with runtime linear in the input length.
/// Constructs that require backtracking semantics, including backreferences and look-around assertions, are rejected
/// when the persisted target is compiled at authorization runtime.
public final class StatementTargetPathMatcher {

    private final Pattern pattern;

    private StatementTargetPathMatcher(Pattern pattern) {
        this.pattern = pattern;
    }

    /// Compiles one persisted target path for bounded runtime matching.
    ///
    /// @throws AuthorizationException if the expression is malformed or uses unsupported RE2 syntax
    public static StatementTargetPathMatcher compile(String expression) {
        try {
            return new StatementTargetPathMatcher(Pattern.compile(expression));
        } catch (PatternSyntaxException exception) {
            throw new AuthorizationException("Statement target path is not a valid regular expression");
        }
    }

    /// Tests the complete path after removing any query string.
    public boolean matches(String path) {
        int queryIndex = path.indexOf('?');
        String pathWithoutQuery = queryIndex < 0 ? path : path.substring(0, queryIndex);
        return this.pattern.matches(pathWithoutQuery);
    }
}
