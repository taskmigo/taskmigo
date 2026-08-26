package io.taskmigo.access;

import io.taskmigo.foundation.domain.DomainException;
import io.taskmigo.foundation.domain.DomainFailureType;

/// Reports an access-domain failure with a transport-neutral category.
public final class AccessException extends DomainException {

    public enum Type {
        BAD_REQUEST,
    }

    AccessException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }
}
