package io.taskmigo.authorization.core;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Validates stable machine-readable names used by authorization definitions.
public final class AuthorizationName {

    private static final Pattern FORMAT = Pattern.compile("[a-zA-Z0-9_-]{6,255}");
    private static final Pattern ROLE_FORMAT = Pattern.compile("[a-zA-Z0-9_ -]{6,255}");
    private static final int MAX_DISPLAY_NAME_LENGTH = 255;

    private AuthorizationName() {}

    public static String required(@Nullable String value, String field) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new AuthorizationException(field + " must match [a-zA-Z0-9_-]{6,255}");
        }
        return value;
    }

    public static String requiredRole(@Nullable String value, String field) {
        if (value == null) {
            throw new AuthorizationException(field + " must match [a-zA-Z0-9_ -]{6,255}");
        }
        String normalized = value.trim();
        if (!ROLE_FORMAT.matcher(normalized).matches()) {
            throw new AuthorizationException(field + " must match [a-zA-Z0-9_ -]{6,255}");
        }
        return normalized;
    }

    public static String requiredDisplayName(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AuthorizationException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new AuthorizationException(field + " must not exceed 255 characters");
        }
        return normalized;
    }
}
