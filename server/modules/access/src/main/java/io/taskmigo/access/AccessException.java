package io.taskmigo.access;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports an access-domain failure with a transport-neutral category.
public final class AccessException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        BAD_REQUEST,
    }

    AccessException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }
}
