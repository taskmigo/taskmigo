package io.taskmigo.authorization.statement;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

/// Matches Statement target paths with bounded RE2-compatible regular-expression semantics.
///
/// The supported syntax is the RE2-compatible subset accepted by the authorization runtime. Constructs that require
/// backtracking, including backreferences and look-around assertions, are rejected when the persisted target is first
/// used by an authorization operation. Matching always applies to the complete request path after removing its query
/// string.
///
/// The concrete regular-expression engine is an implementation detail; callers depend only on the bounded syntax and
/// matching contract exposed here.
public final class StatementTargetPathMatcher {

    private final Pattern pattern;

    private StatementTargetPathMatcher(Pattern pattern) {
        this.pattern = pattern;
    }

    /// Compiles a persisted Statement target path using the bounded target-regex contract.
    ///
    /// @param expression the persisted full-match target path expression
    /// @return a matcher safe to reuse throughout one authorization operation
    /// @throws IllegalArgumentException if the expression is outside the supported RE2-compatible syntax
    public static StatementTargetPathMatcher compile(String expression) {
        try {
            return new StatementTargetPathMatcher(Pattern.compile(expression));
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("Unsupported Statement target path regular expression", exception);
        }
    }

    /// Tests a candidate request path using full-match semantics while ignoring its query string.
    public boolean matches(String requestPath) {
        int queryStart = requestPath.indexOf('?');
        String pathWithoutQuery = queryStart < 0 ? requestPath : requestPath.substring(0, queryStart);
        return this.pattern.matcher(pathWithoutQuery).matches();
    }
}
