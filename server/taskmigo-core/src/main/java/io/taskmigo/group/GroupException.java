package io.taskmigo.group;

import java.io.Serial;

/// Reports a group-domain failure with a transport-neutral category.
public final class GroupException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        BAD_REQUEST,
        NOT_FOUND,
    }

    private final Type type;

    GroupException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public Type type() {
        return this.type;
    }
}
