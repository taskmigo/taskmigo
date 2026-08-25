package io.taskmigo.user;

import java.io.Serial;

/// Reports a user-domain failure with a transport-neutral category.
public final class UserException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type { BAD_REQUEST, NOT_FOUND, CONFLICT }

    private final Type type;

    UserException(Type type, String message) {
        super(message);
        this.type = type;
    }

    UserException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return this.type;
    }
}
