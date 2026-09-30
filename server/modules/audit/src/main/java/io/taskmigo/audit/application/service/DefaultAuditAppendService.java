package io.taskmigo.audit.application.service;

import io.taskmigo.audit.application.port.in.append.AuditAppendService;
import io.taskmigo.audit.application.port.out.AuditLogStore;
import io.taskmigo.audit.application.port.out.AuditTransactionRunner;
import io.taskmigo.audit.event.AuditEvent;

/// Appends one durable audit event at most once at the application boundary.
public final class DefaultAuditAppendService implements AuditAppendService {

    private final AuditLogStore logs;
    private final AuditTransactionRunner transactions;

    public DefaultAuditAppendService(AuditLogStore logs, AuditTransactionRunner transactions) {
        this.logs = logs;
        this.transactions = transactions;
    }

    @Override
    public void append(AuditEvent event) {
        try {
            this.transactions.write(() -> this.logs.append(event));
        } catch (RuntimeException appendFailure) {
            try {
                if (this.transactions.read(() -> this.logs.existsBySourceEventId(event.id()))) {
                    return;
                }
            } catch (RuntimeException verificationFailure) {
                appendFailure.addSuppressed(verificationFailure);
            }
            throw appendFailure;
        }
    }
}
