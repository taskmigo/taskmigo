package io.taskmigo.access;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StatementRepository extends JpaRepository<StatementEntity, UUID> {
    Optional<StatementEntity> findByOriginAndKey(String origin, String key);

    List<StatementEntity> findAllByOriginOrderByKey(String origin);

    List<StatementEntity> findAllByOriginAndOrganizationIdOrderByKey(String origin, UUID organizationId);

    List<StatementEntity> findAllByIdIn(Collection<UUID> ids);
}
