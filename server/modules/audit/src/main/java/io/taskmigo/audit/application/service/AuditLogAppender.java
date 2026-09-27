package io.taskmigo.audit.application.service;

import io.taskmigo.audit.AuditEvent;
import io.taskmigo.audit.application.port.out.AuditLogRepository;
import io.taskmigo.audit.application.port.out.AuditTransactionRunner;

/// Commits one idempotent audit append before the outbox handler returns successfully.
public final class AuditLogAppender {

    private final AuditLogRepository logs;
    private final AuditTransactionRunner transactions;

    public AuditLogAppender(AuditLogRepository logs, AuditTransactionRunner transactions) {
        this.logs = logs;
        this.transactions = transactions;
    }

    public void append(AuditEvent event) {
        this.transactions.write(() -> this.logs.appendIfAbsent(event));
    }
}
