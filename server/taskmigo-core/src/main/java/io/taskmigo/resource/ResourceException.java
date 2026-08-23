package io.taskmigo.resource;

import java.io.Serial;

/// Reports a resource-domain failure together with the error category that API adapters should expose.
public final class ResourceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// Classifies resource failures independently of transport-specific status codes.
    public enum Type {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
    }

    private final Type type;

    ResourceException(Type type, String message) {
        super(message);
        this.type = type;
    }

    ResourceException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    /// Returns the domain error category used by callers to map this failure to an external response.
    public Type type() {
        return this.type;
    }
}
