package io.taskmigo.access;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleRepository extends JpaRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByOriginAndKey(String origin, String key);

    List<RoleEntity> findAllByOriginOrderByKey(String origin);

    List<RoleEntity> findAllByOriginAndOrganizationIdOrderByKey(String origin, UUID organizationId);

    List<RoleEntity> findAllByIdIn(Collection<UUID> ids);
}
