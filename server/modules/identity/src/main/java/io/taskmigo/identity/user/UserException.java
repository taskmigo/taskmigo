package io.taskmigo.identity.user;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports a user-domain failure with a semantic failure category.
public final class UserException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        INVALID_INPUT,
        NOT_FOUND,
        CONFLICT,
    }

    public UserException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }

    public UserException(Type type, String message, Throwable cause) {
        super(DomainFailureType.valueOf(type.name()), message, cause);
    }
}
