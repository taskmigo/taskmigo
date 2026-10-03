package io.taskmigo.audit.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaAuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    Page<AuditLogEntity> findAllByEntityType(String entityType, Pageable pageable);

    List<AuditLogEntity> findAllByActorId(UUID actorId);

    List<AuditLogEntity> findAllByEntityTypeAndEntityId(String entityType, UUID entityId);
}
