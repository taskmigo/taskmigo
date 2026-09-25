package io.taskmigo.authorization.statement;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports a Statement-domain failure with a semantic failure category.
public final class StatementException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Type {
        CONFLICT,
    }

    public StatementException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }

    public StatementException(Type type, String message, Throwable cause) {
        super(DomainFailureType.valueOf(type.name()), message, cause);
    }
}
