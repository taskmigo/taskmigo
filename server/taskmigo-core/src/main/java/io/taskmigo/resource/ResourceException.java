package io.taskmigo.resource;

import java.io.Serial;

public final class ResourceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

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

    public Type type() {
        return this.type;
    }
}
