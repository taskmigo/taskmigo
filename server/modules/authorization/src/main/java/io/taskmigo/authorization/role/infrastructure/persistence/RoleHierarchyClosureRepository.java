package io.taskmigo.authorization.role.infrastructure.persistence;

import io.taskmigo.authorization.role.infrastructure.persistence.RoleHierarchyClosureEntity.RoleHierarchyClosureId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleHierarchyClosureRepository
    extends JpaRepository<RoleHierarchyClosureEntity, RoleHierarchyClosureId>
{
    List<RoleHierarchyClosureEntity> findAllByIdAncestorRoleIdIn(Collection<UUID> ancestorRoleIds);
}
