package io.taskmigo.auth.role;

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
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID>, JpaSpecificationExecutor<RoleEntity> {
    Optional<RoleEntity> findByName(String name);

    List<RoleEntity> findAllByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = "statementIds")
    List<RoleEntity> findDistinctByIdIn(Collection<UUID> ids);

    /// Returns all closure descendants, including each requested ancestor.
    @SuppressWarnings("checkstyle:SpringDataQuery")
    @Query(
        """
        select relation.id.descendantRoleId
        from RoleHierarchyClosureEntity relation
        where relation.id.ancestorRoleId in :ancestorRoleIds
        order by relation.id.descendantRoleId
        """
    )
    List<UUID> findDescendantRoleIds(@Param("ancestorRoleIds") Collection<UUID> ancestorRoleIds);

    Page<RoleEntity> findAllBy(Pageable pageable);

    @SuppressWarnings("checkstyle:SpringDataQuery")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from RoleEntity entity")
    List<RoleEntity> findAllForUpdate();
}
