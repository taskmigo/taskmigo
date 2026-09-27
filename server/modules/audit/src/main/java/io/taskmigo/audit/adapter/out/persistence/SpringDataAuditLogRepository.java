package io.taskmigo.audit.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    boolean existsBySourceEventId(UUID sourceEventId);
    Page<AuditLogEntity> findByEntityType(String entityType, Pageable pageable);
}
