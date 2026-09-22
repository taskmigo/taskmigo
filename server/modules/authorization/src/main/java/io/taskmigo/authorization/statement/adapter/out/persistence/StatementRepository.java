package io.taskmigo.authorization.statement.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/// Owns database access to Statement rows and their persistence-owned revision metadata.
public interface StatementRepository
    extends JpaRepository<StatementEntity, UUID>, JpaSpecificationExecutor<StatementEntity>
{
    Optional<StatementEntity> findByCode(String code);

    List<StatementEntity> findAllByIdIn(Collection<UUID> ids);
}
