package io.taskmigo.audit;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports a semantic audit-query failure.
public final class AuditException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuditException(String message) {
        super(DomainFailureType.INVALID_INPUT, message);
    }
}
