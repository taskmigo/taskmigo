package io.taskmigo.audit.application;

import io.taskmigo.audit.application.append.AuditAppendService;
import io.taskmigo.audit.event.AuditEvent;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// Persists audit data synchronously as part of the caller-owned mutation transaction.
public class DefaultAuditAppendService implements AuditAppendService {

    private final AuditLogStore logs;

    public DefaultAuditAppendService(AuditLogStore logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void append(AuditEvent event) {
        this.logs.append(event);
    }
}
