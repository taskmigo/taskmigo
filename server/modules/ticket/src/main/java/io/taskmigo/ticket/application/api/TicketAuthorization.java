package io.taskmigo.ticket.application.api;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;

/// Typed Object Authorization contracts published by the Ticket feature.
public final class TicketAuthorization {

    private TicketAuthorization() {}

    public interface List extends ObjectAuthorizationSchema<TicketInfo> {}

    public interface Read extends ObjectAuthorizationSchema<TicketInfo> {}

    public interface Update extends ObjectAuthorizationSchema<TicketInfo> {}
}
