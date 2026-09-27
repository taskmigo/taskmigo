package io.taskmigo.authorization.statement;

import com.google.re2j.Pattern;

/// Compiles and matches one Statement API target path with the RE2 regular-expression engine.
///
/// The supported contract includes literals, character classes, alternation, repetition, and anchors. Lookarounds and
/// backreferences are rejected because RE2 does not implement backtracking-dependent constructs. Matching always
/// applies to the complete path after its query string has been removed.
public final class StatementTargetPattern {

    private final Pattern pattern;

    private StatementTargetPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    /// Compiles a persisted target path using the bounded RE2 syntax.
    public static StatementTargetPattern compile(String source) {
        return new StatementTargetPattern(Pattern.compile(source));
    }

    /// Tests whether the complete path, excluding any query string, matches this target.
    public boolean matches(String path) {
        int queryStart = path.indexOf('?');
        String pathWithoutQuery = queryStart < 0 ? path : path.substring(0, queryStart);
        return this.pattern.matcher(pathWithoutQuery).matches();
    }
}
