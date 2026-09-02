package io.taskmigo.auth.authorization.condition;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports invalid or conflicting authorization resource input.
public final class AuthorizationException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthorizationException(String message) {
        super(DomainFailureType.BAD_REQUEST, message);
    }
}
