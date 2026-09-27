package io.taskmigo.audit.model;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports semantic failures in the audit capability.
public final class AuditException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private AuditException(DomainFailureType type, String message) {
        super(type, message);
    }

    static AuditException unsupportedEntityType(String entityType) {
        return new AuditException(DomainFailureType.INVALID_INPUT, "Unsupported audit entity type: " + entityType);
    }
}
