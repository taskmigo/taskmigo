package io.taskmigo.authorization.role;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports a Role-domain failure with a semantic failure category.
public final class RoleException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        INVALID_INPUT,
        CONFLICT,
    }

    public RoleException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }

    public RoleException(Type type, String message, Throwable cause) {
        super(DomainFailureType.valueOf(type.name()), message, cause);
    }
}
