package io.taskmigo.audit.application;

import io.taskmigo.audit.AuditException;
import io.taskmigo.audit.application.query.AuditQueryService;
import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/// Implements supported entity-type validation and newest-first audit queries.
@Transactional(readOnly = true)
public class DefaultAuditQueryService implements AuditQueryService {

    private static final Set<String> SUPPORTED_ENTITY_TYPES = Set.of("user");

    private final AuditLogStore logs;

    public DefaultAuditQueryService(AuditLogStore logs) {
        this.logs = logs;
    }

    @Override
    public OffsetPage<AuditLog> list(String entityType, int page, int pageSize) {
        if (!SUPPORTED_ENTITY_TYPES.contains(entityType)) {
            throw new AuditException("Unsupported audit entity type: " + entityType);
        }
        return this.logs.list(entityType, page, pageSize);
    }
}
