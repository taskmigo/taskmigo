package io.taskmigo.query;

import java.io.Serial;

/// Identifies invalid client-supplied sort intent independently of any transport adapter.
public class SortByException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SortByException(String message) {
        super(message);
    }

    public SortByException(String message, Throwable cause) {
        super(message, cause);
    }
}
