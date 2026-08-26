package io.taskmigo.group;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports a group-domain failure with a transport-neutral category.
public final class GroupException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        BAD_REQUEST,
        NOT_FOUND,
    }

    GroupException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }
}
