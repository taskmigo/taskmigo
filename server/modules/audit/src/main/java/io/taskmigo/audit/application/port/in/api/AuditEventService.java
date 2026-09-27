package io.taskmigo.audit.application.port.in.api;

import io.taskmigo.audit.model.AuditMutationEvent;

/// Accepts immutable mutation snapshots for durable background processing.
public interface AuditEventService {
    void publish(AuditMutationEvent event);
}
