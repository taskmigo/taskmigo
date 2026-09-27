package io.taskmigo.audit.application.service;

import io.taskmigo.audit.application.port.in.api.AuditEventService;
import io.taskmigo.audit.application.port.out.AuditEventPublisher;
import io.taskmigo.audit.model.AuditMutationEvent;

/// Publishes immutable audit events without exposing framework event APIs to callers.
public final class DefaultAuditEventService implements AuditEventService {

    private final AuditEventPublisher publisher;

    public DefaultAuditEventService(AuditEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(AuditMutationEvent event) {
        this.publisher.publish(event);
    }
}
