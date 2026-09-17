package io.taskmigo.authorization.role;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports a Role-domain failure with a transport-neutral category.
public final class RoleException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        BAD_REQUEST,
    }

    public RoleException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }
}
