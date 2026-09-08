package io.taskmigo.query;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// Identifies an immutable API-visible query path.
public record QueryPath(List<String> segments) {
    public QueryPath {
        segments = List.copyOf(segments);
        if (segments.isEmpty() || segments.stream().anyMatch(segment -> segment == null || segment.isBlank())) {
            throw new IllegalArgumentException("query path must contain non-blank segments");
        }
    }

    /// Creates a path from ordered API field segments.
    public static QueryPath of(String... segments) {
        return new QueryPath(Arrays.asList(segments));
    }

    /// Parses a dot-separated API path.
    public static QueryPath parse(String value) {
        Objects.requireNonNull(value);
        return of(value.split("\\.", -1));
    }

    /// Returns the canonical dot-separated path text.
    public String text() {
        return String.join(".", this.segments);
    }
}
