package io.taskmigo.audit.application;

import io.taskmigo.audit.application.privacy.AuditPrivacyService;
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
