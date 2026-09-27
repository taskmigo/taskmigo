package io.taskmigo.audit.application.port.in.internal;

import io.taskmigo.audit.model.AuditMutationEvent;

/// Appends one externalized mutation event idempotently.
public interface AuditAppendService {
    void append(AuditMutationEvent event);
}
