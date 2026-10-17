package io.taskmigo.query;

import java.util.Objects;

/// Describes one requested database-side ordering over an approved Query Schema field.
public record QuerySort(QueryPath path, Direction direction) {
    public QuerySort {
        Objects.requireNonNull(path);
        Objects.requireNonNull(direction);
    }

    public enum Direction {
        ASC,
        DESC,
    }
}
