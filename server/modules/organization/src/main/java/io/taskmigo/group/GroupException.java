package io.taskmigo.group;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;

/// Reports a group-domain failure with a transport-neutral category.
public final class GroupException extends DomainException {

    public enum Type {
        BAD_REQUEST,
        NOT_FOUND,
    }

    GroupException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }
}
