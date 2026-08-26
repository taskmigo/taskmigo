package io.taskmigo.access;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;

/// Reports an access-domain failure with a transport-neutral category.
public final class AccessException extends DomainException {

    public enum Type {
        BAD_REQUEST,
    }

    AccessException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }
}
