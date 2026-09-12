package io.taskmigo.query;

import java.io.Serial;

/// Identifies invalid client-supplied filterBy input.
public class FilterByException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public FilterByException(String message) {
        super(message);
    }

    public FilterByException(String message, Throwable cause) {
        super(message, cause);
    }
}
