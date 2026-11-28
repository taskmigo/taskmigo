package io.taskmigo.ticket.field.application.api;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports a shared field lifecycle or validation failure independently of HTTP transport.
public final class TicketFieldException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TicketFieldException(DomainFailureType type, String message) {
        super(type, message);
    }

    public TicketFieldException(DomainFailureType type, String message, Throwable cause) {
        super(type, message, cause);
    }
}
