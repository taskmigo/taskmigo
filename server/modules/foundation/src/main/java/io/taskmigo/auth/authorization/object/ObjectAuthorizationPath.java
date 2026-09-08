package io.taskmigo.auth.authorization.object;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// Identifies an immutable API-visible path in an Object Authorization Schema.
public record ObjectAuthorizationPath(List<String> segments) {
    public ObjectAuthorizationPath {
        segments = List.copyOf(segments);
        if (segments.isEmpty() || segments.stream().anyMatch(segment -> segment == null || segment.isBlank())) {
            throw new IllegalArgumentException("object authorization path must contain non-blank segments");
        }
    }

    /// Creates a path from ordered API field segments.
    public static ObjectAuthorizationPath of(String... segments) {
        return new ObjectAuthorizationPath(Arrays.asList(segments));
    }

    /// Parses a dot-separated API path.
    public static ObjectAuthorizationPath parse(String value) {
        Objects.requireNonNull(value);
        return of(value.split("\\.", -1));
    }

    /// Returns the canonical dot-separated path text.
    public String text() {
        return String.join(".", this.segments);
    }
}
