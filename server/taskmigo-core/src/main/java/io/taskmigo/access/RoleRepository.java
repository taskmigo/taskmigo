package io.taskmigo.access;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleRepository extends JpaRepository<RoleEntity, UUID> {
    List<RoleEntity> findAllByIdIn(Collection<UUID> ids);
}
