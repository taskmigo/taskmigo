package io.taskmigo.organization;

import java.io.Serial;

/// Reports an organization-domain failure with a transport-neutral category.
public final class OrganizationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
    }

    private final Type type;

    OrganizationException(Type type, String message) {
        super(message);
        this.type = type;
    }

    OrganizationException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return this.type;
    }
}
