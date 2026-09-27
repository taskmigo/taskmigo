package io.taskmigo.authorization.statement;

import com.google.re2j.Pattern;
import io.taskmigo.authorization.core.AuthorizationException;

/// Compiles and applies Taskmigo's bounded regular-expression contract for Statement API target paths.
///
/// The expression syntax is intentionally smaller than RE2. It is limited to printable ASCII expressions with
/// literals, '.', character classes, capturing groups, alternation, anchors, '*', '+', '?', ASCII shorthand classes,
/// and escaped punctuation. Engine extensions, Unicode/property escapes, counted repetition, and excessive grouping
/// are rejected before RE2/J is invoked.
public final class StatementTargetPathMatcher {

    private static final int MAX_EXPRESSION_LENGTH = 2000;
    private static final int MAX_GROUP_NESTING = 32;
    private static final String ASCII_SHORTHAND_ESCAPES = "dDsSwW";
    private static final String INVALID_EXPRESSION_MESSAGE =
        "Statement target path is not a valid regular expression in the supported target subset";

    private final Pattern pattern;

    private StatementTargetPathMatcher(Pattern pattern) {
        this.pattern = pattern;
    }

    /// Compiles one persisted target path for bounded runtime matching.
    ///
    /// @throws AuthorizationException if the expression is malformed or outside the supported target subset
    public static StatementTargetPathMatcher compile(String expression) {
        validateSafeSubset(expression);
        try {
            return new StatementTargetPathMatcher(Pattern.compile(expression));
        } catch (RuntimeException ignored) {
            throw invalidExpression();
        }
    }

    /// Tests the complete path after removing any query string.
    public boolean matches(String path) {
        int queryIndex = path.indexOf('?');
        String pathWithoutQuery = queryIndex < 0 ? path : path.substring(0, queryIndex);
        return this.pattern.matches(pathWithoutQuery);
    }

    private static void validateSafeSubset(String expression) {
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            throw invalidExpression();
        }

        int groupNesting = 0;
        boolean characterClass = false;
        int index = 0;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (!isPrintableAscii(current)) {
                throw invalidExpression();
            }
            if (current == '\\') {
                int escapedIndex = index + 1;
                if (escapedIndex >= expression.length() || !isSupportedEscape(expression.charAt(escapedIndex))) {
                    throw invalidExpression();
                }
                index = escapedIndex + 1;
                continue;
            }
            if (characterClass) {
                if (current == ']') {
                    characterClass = false;
                }
                index++;
                continue;
            }
            if (current == '[') {
                characterClass = true;
                index++;
                continue;
            }
            if (current == '{' || current == '}') {
                throw invalidExpression();
            }
            if (current == '(') {
                if (index + 1 < expression.length() && expression.charAt(index + 1) == '?') {
                    throw invalidExpression();
                }
                groupNesting++;
                if (groupNesting > MAX_GROUP_NESTING) {
                    throw invalidExpression();
                }
            } else if (current == ')' && groupNesting > 0) {
                groupNesting--;
            }
            index++;
        }
        if (characterClass || groupNesting != 0) {
            throw invalidExpression();
        }
    }

    private static boolean isPrintableAscii(char value) {
        return value >= 0x20 && value <= 0x7e;
    }

    private static boolean isSupportedEscape(char value) {
        return (
            isPrintableAscii(value) &&
            (ASCII_SHORTHAND_ESCAPES.indexOf(value) >= 0 || !Character.isLetterOrDigit(value))
        );
    }

    private static AuthorizationException invalidExpression() {
        return new AuthorizationException(INVALID_EXPRESSION_MESSAGE);
    }
}
