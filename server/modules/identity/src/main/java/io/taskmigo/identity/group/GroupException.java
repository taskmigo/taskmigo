package io.taskmigo.identity.group;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports a group-domain failure with a semantic failure category.
public final class GroupException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        INVALID_INPUT,
        NOT_FOUND,
    }

    public GroupException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }
}
