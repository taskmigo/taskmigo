package io.taskmigo.audit.adapter.out.persistence;

import io.taskmigo.audit.application.port.out.AuditLogStore;
import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.foundation.jackson.TaskmigoJackson;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/// Persists immutable audit records and their field-level changes through JPA.
@Repository
@ConditionalOnProperty(prefix = "taskmigo.audit", name = "enabled", havingValue = "true")
public class JpaAuditLogStore implements AuditLogStore {

    private static final JsonMapper JSON = TaskmigoJackson.configure(JsonMapper.builder()).build();
    private static final String UNKNOWN_USER = "Unknown user";
    private static final Set<String> USER_PII_FIELDS = Set.of("username", "firstName", "lastName", "emails");

    private final JpaAuditLogRepository logs;

    JpaAuditLogStore(JpaAuditLogRepository logs) {
        this.logs = logs;
    }

    @Override
    public void append(AuditEvent event) {
        this.logs.saveAndFlush(
            new AuditLogEntity(
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
            entity.entityType,
            entity.entityId,
            new AuditActor(entity.actorId, entity.actorUsername),
            entity.occurredAt,
            Arrays.asList(this.readChanges(entity.changesJson))
        );
    }

    @Override
    public void scrubUser(UUID userId) {
        var actorLogs = this.logs.findAllByActorId(userId);
        actorLogs.forEach(log -> log.actorUsername = UNKNOWN_USER);

        var userLogs = this.logs.findAllByEntityTypeAndEntityId("user", userId);
        for (AuditLogEntity log : userLogs) {
            AuditChange[] changes = this.readChanges(log.changesJson);
            boolean changed = false;
            for (int index = 0; index < changes.length; index++) {
                AuditChange change = changes[index];
                if (USER_PII_FIELDS.contains(change.field()) && !change.sensitive()) {
                    changes[index] = AuditChange.sensitive(change.field());
                    changed = true;
                }
            }
            if (changed) {
                log.changesJson = this.writeChanges(changes);
            }
        }

        if (!actorLogs.isEmpty()) {
            this.logs.saveAll(actorLogs);
        }
        if (!userLogs.isEmpty()) {
            this.logs.saveAll(userLogs);
        }
        this.logs.flush();
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
