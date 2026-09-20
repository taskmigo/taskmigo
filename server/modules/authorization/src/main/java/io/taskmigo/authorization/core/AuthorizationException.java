package io.taskmigo.authorization.core;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports invalid authorization input.
public final class AuthorizationException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthorizationException(String message) {
        super(DomainFailureType.INVALID_INPUT, message);
    }
}
