package io.taskmigo.audit.application.port.out;

import io.taskmigo.audit.model.AuditMutationEvent;

/// Publishes audit events to the configured application-event infrastructure.
public interface AuditEventPublisher {
    void publish(AuditMutationEvent event);
}
