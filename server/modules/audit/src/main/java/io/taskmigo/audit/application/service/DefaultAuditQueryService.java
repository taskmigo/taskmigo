package io.taskmigo.audit.application.service;

import io.taskmigo.audit.AuditException;
import io.taskmigo.audit.application.port.in.query.AuditQueryService;
import io.taskmigo.audit.application.port.out.AuditLogStore;
import io.taskmigo.audit.application.port.out.AuditTransactionRunner;
import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;
import java.util.Set;

/// Implements supported entity-type validation and newest-first audit queries.
public final class DefaultAuditQueryService implements AuditQueryService {

    private static final Set<String> SUPPORTED_ENTITY_TYPES = Set.of("user");

    private final AuditLogStore logs;
    private final AuditTransactionRunner transactions;

    public DefaultAuditQueryService(AuditLogStore logs, AuditTransactionRunner transactions) {
        this.logs = logs;
        this.transactions = transactions;
    }

    @Override
    public OffsetPage<AuditLog> list(String entityType, int page, int pageSize) {
        if (!SUPPORTED_ENTITY_TYPES.contains(entityType)) {
            throw new AuditException("Unsupported audit entity type: " + entityType);
        }
        return this.transactions.read(() -> this.logs.list(entityType, page, pageSize));
    }
}
