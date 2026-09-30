package io.taskmigo.identity.user.application.port.out;

import io.taskmigo.audit.event.AuditEvent;

/// Publishes User mutation audit events without exposing Spring event infrastructure to application services.
public interface UserAuditPublisher {

    void publish(AuditEvent event);
}
