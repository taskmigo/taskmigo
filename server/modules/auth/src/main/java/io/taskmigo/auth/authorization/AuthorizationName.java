package io.taskmigo.auth.authorization;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Validates stable machine-readable names used by authorization resources.
public final class AuthorizationName {

    private static final Pattern FORMAT = Pattern.compile("[a-zA-Z0-9_-]{6,255}");
    private static final Pattern ROLE_FORMAT = Pattern.compile("[a-zA-Z0-9_ -]{6,255}");

    private AuthorizationName() {}

    public static String required(@Nullable String value, String field) {
        if (value == null || !FORMAT.matcher(value).matches()) throw new AuthorizationException(
            field + " must match [a-zA-Z0-9_-]{6,255}"
        );
        return value;
    }

    public static String requiredRole(@Nullable String value, String field) {
        if (value == null || !ROLE_FORMAT.matcher(value).matches()) throw new AuthorizationException(
            field + " must match [a-zA-Z0-9_ -]{6,255}"
        );
        return value;
    }
}
