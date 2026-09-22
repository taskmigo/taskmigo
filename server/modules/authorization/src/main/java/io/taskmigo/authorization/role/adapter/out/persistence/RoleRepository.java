package io.taskmigo.authorization.role.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

/// Owns database access to Role rows while keeping command, hierarchy, and optimized-read loading explicit.
public interface RoleRepository extends JpaRepository<RoleEntity, UUID>, JpaSpecificationExecutor<RoleEntity> {
    @EntityGraph(attributePaths = "statementIds")
    Optional<RoleEntity> findByCode(String code);

    @EntityGraph(attributePaths = "statementIds")
    Optional<RoleEntity> findDistinctById(UUID id);

    List<RoleEntity> findAllByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = "statementIds")
    List<RoleEntity> findDistinctByIdIn(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RoleEntity> findAllBy();
}
