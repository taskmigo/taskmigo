package io.taskmigo.audit.application.service;

import io.taskmigo.audit.application.port.in.internal.AuditAppendService;
import io.taskmigo.audit.application.port.out.AuditLogAppendRepository;
import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.audit.model.AuditMutationEvent;

/// Appends externalized audit events idempotently using the stable source event identifier.
public final class DefaultAuditAppendService implements AuditAppendService {

    private final AuditLogAppendRepository logs;

    public DefaultAuditAppendService(AuditLogAppendRepository logs) {
        this.logs = logs;
    }

    @Override
    public void append(AuditMutationEvent event) {
        if (this.logs.existsBySourceEventId(event.eventId())) {
            return;
        }
        this.logs.append(AuditLog.from(event));
    }
}
