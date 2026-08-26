package io.taskmigo.project;

import java.io.Serial;

/// Reports a project-domain failure with a transport-neutral category.
public final class ProjectException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
    }

    private final Type type;

    ProjectException(Type type, String message) {
        super(message);
        this.type = type;
    }

    ProjectException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return this.type;
    }
}
