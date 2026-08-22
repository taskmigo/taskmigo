package io.taskmigo.resource;

public final class ResourceException extends RuntimeException {
    public enum Type {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT
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
        return type;
    }
}
