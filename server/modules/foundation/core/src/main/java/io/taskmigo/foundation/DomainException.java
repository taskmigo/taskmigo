package io.taskmigo.foundation;

import java.io.Serial;

/// Base class for transport-neutral failures raised by business capabilities.
public abstract class DomainException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DomainFailureType type;

    protected DomainException(DomainFailureType type, String message) {
        super(message);
        this.type = type;
    }

    protected DomainException(DomainFailureType type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public final DomainFailureType type() {
        return this.type;
    }
}
