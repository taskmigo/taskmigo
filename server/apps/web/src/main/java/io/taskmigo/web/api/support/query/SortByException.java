package io.taskmigo.web.api.support.query;

/// Backward-compatible Web subtype of the transport-neutral query sort validation failure.
public class SortByException extends io.taskmigo.query.SortByException {

    public SortByException(String message) {
        super(message);
    }

    public SortByException(String message, Throwable cause) {
        super(message, cause);
    }
}
