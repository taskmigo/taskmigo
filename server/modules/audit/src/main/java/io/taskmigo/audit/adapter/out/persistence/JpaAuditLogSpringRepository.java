package io.taskmigo.audit.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaAuditLogSpringRepository extends JpaRepository<AuditLogEntity, UUID> {
    boolean existsBySourceEventId(UUID sourceEventId);

    Page<AuditLogEntity> findByEntityTypeOrderByOccurredAtDescIdDesc(String entityType, Pageable pageable);
}
