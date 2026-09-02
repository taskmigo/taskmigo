package io.taskmigo.access;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RoleRepository extends JpaRepository<RoleEntity, UUID> {
    List<RoleEntity> findAllByIdIn(Collection<UUID> ids);

    Page<RoleEntity> findAllBy(Pageable pageable);

    @SuppressWarnings("checkstyle:SpringDataQuery")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from RoleEntity entity")
    List<RoleEntity> findAllForUpdate();
}
