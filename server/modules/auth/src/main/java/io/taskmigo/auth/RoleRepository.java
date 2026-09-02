package io.taskmigo.auth;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RoleRepository extends JpaRepository<RoleEntity, UUID>, JpaSpecificationExecutor<RoleEntity> {
    Optional<RoleEntity> findByName(String name);
    List<RoleEntity> findAllByIdIn(Collection<UUID> ids);

    Page<RoleEntity> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "childRoles")
    List<RoleEntity> findAllByOrderByIdAsc();

    @SuppressWarnings("checkstyle:SpringDataQuery")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from RoleEntity entity")
    List<RoleEntity> findAllForUpdate();
}
