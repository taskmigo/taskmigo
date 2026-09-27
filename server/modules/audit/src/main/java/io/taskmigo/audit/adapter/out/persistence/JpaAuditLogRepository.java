package io.taskmigo.audit.adapter.out.persistence;

import io.taskmigo.audit.AuditEvent;
import io.taskmigo.audit.AuditLog;
import io.taskmigo.audit.application.port.out.AuditLogRepository;
import io.taskmigo.foundation.OffsetPage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/// JPA adapter for append-only audit persistence and offset-paginated reads.
@Component
final class JpaAuditLogRepository implements AuditLogRepository {

    private final JpaAuditLogSpringRepository logs;

    JpaAuditLogRepository(JpaAuditLogSpringRepository logs) {
        this.logs = logs;
    }

    @Override
    public void appendIfAbsent(AuditEvent event) {
        if (this.logs.existsBySourceEventId(event.eventId())) {
            return;
        }
        try {
            this.logs.saveAndFlush(AuditLogEntity.from(event));
        } catch (DataIntegrityViolationException exception) {
            if (!this.logs.existsBySourceEventId(event.eventId())) {
                throw exception;
            }
        }
    }

    @Override
    public OffsetPage<AuditLog> list(String entityType, int page, int pageSize) {
        var result = this.logs.findByEntityTypeOrderByOccurredAtDescIdDesc(
            entityType,
            PageRequest.of(page - 1, pageSize)
        );
        return new OffsetPage<>(
            result.getContent().stream().map(AuditLogEntity::toModel).toList(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }
}
