package io.taskmigo.audit.persistence;

import io.taskmigo.audit.model.AuditActor;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaAuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    Page<AuditLogEntity> findAllByEntityType(String entityType, Pageable pageable);

    List<AuditLogEntity> findAllByActorIdAndActorType(UUID actorId, AuditActor.Type actorType);

    List<AuditLogEntity> findAllByEntityTypeAndEntityId(String entityType, String entityId);
}
