package io.taskmigo.audit.application.service;

import io.taskmigo.audit.application.port.in.privacy.AuditPrivacyService;
import io.taskmigo.audit.application.port.out.AuditLogStore;
import io.taskmigo.audit.application.port.out.AuditTransactionRunner;
import java.util.UUID;

/// Performs privacy scrubbing inside the caller-owned business transaction.
public final class DefaultAuditPrivacyService implements AuditPrivacyService {

    private final AuditLogStore logs;
    private final AuditTransactionRunner transactions;

    public DefaultAuditPrivacyService(AuditLogStore logs, AuditTransactionRunner transactions) {
        this.logs = logs;
        this.transactions = transactions;
    }

    @Override
    public void scrubUser(UUID userId) {
        this.transactions.write(() -> this.logs.scrubUser(userId));
    }
}
