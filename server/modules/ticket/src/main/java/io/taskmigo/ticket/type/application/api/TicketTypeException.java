package io.taskmigo.ticket.type.application.api;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports Ticket Type and template conflicts without coupling the domain to HTTP.
public final class TicketTypeException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TicketTypeException(DomainFailureType type, String message) {
        super(type, message);
    }

    public TicketTypeException(DomainFailureType type, String message, Throwable cause) {
        super(type, message, cause);
    }
}
