package io.taskmigo.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface StatementRepository extends JpaRepository<StatementEntity, UUID>, JpaSpecificationExecutor<StatementEntity> {
    boolean existsByName(String name);

    java.util.Optional<StatementEntity> findByName(String name);

    Page<StatementEntity> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "conditions")
    List<StatementEntity> findAllByIdIn(Collection<UUID> ids);
}
