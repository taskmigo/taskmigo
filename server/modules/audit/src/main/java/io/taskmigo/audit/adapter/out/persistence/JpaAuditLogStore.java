package io.taskmigo.audit.adapter.out.persistence;

import io.taskmigo.audit.application.port.out.AuditLogStore;
import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/// Persists immutable audit records and their field-level changes through JPA.
@Repository
@ConditionalOnProperty(prefix = "taskmigo.audit", name = "enabled", havingValue = "true")
public class JpaAuditLogStore implements AuditLogStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JpaAuditLogRepository logs;

    JpaAuditLogStore(JpaAuditLogRepository logs) {
        this.logs = logs;
    }

    @Override
    public void append(AuditEvent event) {
        this.logs.saveAndFlush(
            new AuditLogEntity(
                event.id(),
                event.id(),
                event.entityType(),
                event.entityId(),
                event.actor().id(),
                event.actor().username(),
                event.occurredAt(),
                this.writeChanges(event.changes().toArray(AuditChange[]::new))
            )
        );
    }

    @Override
    public OffsetPage<AuditLog> list(String entityType, int page, int pageSize) {
        var order = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
        var result = this.logs.findAllByEntityType(entityType, PageRequest.of(page - 1, pageSize, order));
        return new OffsetPage<>(
            result.map(this::toModel).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    private AuditLog toModel(AuditLogEntity entity) {
        return new AuditLog(
            entity.id,
            entity.sourceEventId,
            entity.entityType,
            entity.entityId,
            new AuditActor(entity.actorId, entity.actorUsername),
            entity.occurredAt,
            Arrays.asList(this.readChanges(entity.changesJson))
        );
    }

    private String writeChanges(AuditChange[] changes) {
        try {
            return JSON.writeValueAsString(changes);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize audit changes", exception);
        }
    }

    private AuditChange[] readChanges(String changesJson) {
        try {
            return JSON.readValue(changesJson, AuditChange[].class);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize audit changes", exception);
        }
    }
}
