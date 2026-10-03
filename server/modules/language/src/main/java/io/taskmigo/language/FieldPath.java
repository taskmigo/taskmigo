package io.taskmigo.language;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// Represents the canonical source-facing path of a semantic resource field.
public record FieldPath(List<String> segments) implements Comparable<FieldPath> {
    public FieldPath {
        segments = List.copyOf(segments);
        if (segments.isEmpty() || segments.stream().anyMatch(segment -> segment.isBlank() || segment.contains("."))) {
            throw new IllegalArgumentException("field path requires nonblank dot-free segments");
        }
    }

    /// Parses a dot-delimited canonical field path.
    public static FieldPath parse(String value) {
        Objects.requireNonNull(value);
        return new FieldPath(Arrays.asList(value.split("\\.", -1)));
    }

    /// Returns the dot-delimited canonical form used in Language source.
    public String text() {
        return String.join(".", this.segments);
    }

    @Override
    public int compareTo(FieldPath other) {
        return this.text().compareTo(other.text());
    }
}
