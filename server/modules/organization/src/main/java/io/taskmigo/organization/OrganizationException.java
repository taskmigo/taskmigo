package io.taskmigo.organization;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports an organization-domain failure with a transport-neutral category.
public final class OrganizationException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
    }

    OrganizationException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }

    OrganizationException(Type type, String message, Throwable cause) {
        super(DomainFailureType.valueOf(type.name()), message, cause);
    }
}
