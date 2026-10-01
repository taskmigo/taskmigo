package io.taskmigo.audit.application.service;

import io.taskmigo.audit.application.port.in.append.AuditAppendService;
import io.taskmigo.audit.application.port.out.AuditLogStore;
import io.taskmigo.audit.application.port.out.AuditTransactionRunner;
import io.taskmigo.audit.event.AuditEvent;

/// Persists audit data synchronously as part of the caller-owned mutation transaction.
public final class DefaultAuditAppendService implements AuditAppendService {

    private final AuditLogStore logs;
    private final AuditTransactionRunner transactions;

    public DefaultAuditAppendService(AuditLogStore logs, AuditTransactionRunner transactions) {
        this.logs = logs;
        this.transactions = transactions;
    }

    @Override
    public void append(AuditEvent event) {
        this.transactions.write(() -> this.logs.append(event));
    }
}
