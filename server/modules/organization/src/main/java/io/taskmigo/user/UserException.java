package io.taskmigo.user;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports a user-domain failure with a transport-neutral category.
public final class UserException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
    }

    UserException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }

    UserException(Type type, String message, Throwable cause) {
        super(DomainFailureType.valueOf(type.name()), message, cause);
    }
}
