package io.taskmigo.web.api.support.query;

import java.io.Serial;

/// Backward-compatible Web subtype of the transport-neutral query sort validation failure.
public class SortByException extends io.taskmigo.query.SortByException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SortByException(String message) {
        super(message);
    }

    public SortByException(String message, Throwable cause) {
        super(message, cause);
    }
}
