package io.taskmigo.query;

/// Identifies invalid client-supplied filterBy input.
public class FilterByException extends RuntimeException {
    public FilterByException(String message) {
        super(message);
    }

    public FilterByException(String message, Throwable cause) {
        super(message, cause);
    }
}
