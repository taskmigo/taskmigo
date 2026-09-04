package io.taskmigo.auth.authorization.statement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StatementRepository
    extends JpaRepository<StatementEntity, UUID>, JpaSpecificationExecutor<StatementEntity>
{
    boolean existsByName(String name);

    Optional<StatementEntity> findByName(String name);

    Page<StatementEntity> findAllBy(Pageable pageable);

    List<StatementEntity> findAllByIdIn(Collection<UUID> ids);
}
