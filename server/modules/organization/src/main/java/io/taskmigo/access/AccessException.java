package io.taskmigo.access;

import java.io.Serial;

/// Reports an access-domain failure with a transport-neutral category.
public final class AccessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        BAD_REQUEST,
    }

    private final Type type;

    AccessException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public Type type() {
        return this.type;
    }
}
