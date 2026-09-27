package io.taskmigo.audit.adapter.out.persistence;

import io.taskmigo.audit.application.port.out.AuditLogAppendRepository;
import io.taskmigo.audit.application.port.out.AuditLogQueryRepository;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditEntityType;
import io.taskmigo.audit.model.AuditFieldChange;
import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JpaAuditLogStore implements AuditLogAppendRepository, AuditLogQueryRepository {

    private static final TypeReference<List<AuditFieldChange>> CHANGE_LIST = new TypeReference<>() {};
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final SpringDataAuditLogRepository logs;

    JpaAuditLogStore(SpringDataAuditLogRepository logs) {
        this.logs = logs;
    }

    @Override
    public boolean existsBySourceEventId(UUID sourceEventId) {
        return this.logs.existsBySourceEventId(sourceEventId);
    }

    @Override
    public void append(AuditLog log) {
        this.logs.saveAndFlush(
                new AuditLogEntity(
                    log.id(),
                    log.sourceEventId(),
                    log.entityType(),
                    log.entityId(),
                    log.actor().id(),
                    log.actor().username(),
                    log.occurredAt(),
                    JSON.writeValueAsString(log.changes())
                )
            );
    }

    @Override
    public OffsetPage<AuditLog> list(AuditEntityType entityType, int page, int perPage) {
        PageRequest request = PageRequest.of(
            page - 1,
            perPage,
            Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))
        );
        var result = this.logs.findByEntityType(entityType.code(), request);
        return new OffsetPage<>(
            result.getContent().stream().map(this::toLog).toList(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    private AuditLog toLog(AuditLogEntity entity) {
        return new AuditLog(
            entity.id,
            entity.sourceEventId,
            entity.entityType,
            entity.entityId,
            new AuditActor(entity.actorId, entity.actorUsername),
            entity.occurredAt,
            JSON.readValue(entity.changesJson, CHANGE_LIST)
        );
    }
}
