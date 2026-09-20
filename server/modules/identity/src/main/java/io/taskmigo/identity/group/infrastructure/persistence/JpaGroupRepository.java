package io.taskmigo.identity.group.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaGroupRepository extends JpaRepository<GroupEntity, UUID>, JpaSpecificationExecutor<GroupEntity> {
    Optional<GroupEntity> findByCode(String code);

    @SuppressWarnings("checkstyle:SpringDataQuery")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from GroupEntity entity")
    List<GroupEntity> findAllForUpdate();

    List<GroupEntity> findDistinctByIdIn(Collection<UUID> ids);

    /// Returns all closure descendants, including each requested ancestor.
    @SuppressWarnings("checkstyle:SpringDataQuery")
    @Query(
        """
        select relation.id.descendantGroupId
        from GroupHierarchyClosureEntity relation
        where relation.id.ancestorGroupId in :ancestorGroupIds
        order by relation.id.descendantGroupId
        """
    )
    List<UUID> findDescendantGroupIds(@Param("ancestorGroupIds") Collection<UUID> ancestorGroupIds);
}
