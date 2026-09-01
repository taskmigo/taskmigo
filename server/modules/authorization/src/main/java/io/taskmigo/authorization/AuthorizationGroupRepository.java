package io.taskmigo.authorization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuthorizationGroupRepository extends JpaRepository<AuthorizationGroupEntity, UUID> {
    Optional<AuthorizationGroupEntity> findByOrganizationIdAndKey(UUID organizationId, String key);

    Optional<AuthorizationGroupEntity> findByOrganizationIdIsNullAndKey(String key);

    @Query(
        "select group from AuthorizationGroupEntity group " +
            "where group.organizationId = :organizationId or group.organizationId is null order by group.key"
    )
    List<AuthorizationGroupEntity> findRelevant(@Param("organizationId") UUID organizationId);

    List<AuthorizationGroupEntity> findAllByOrganizationIdIsNullOrderByKey();

    @Query(
        "select distinct group from AuthorizationGroupEntity group join group.memberIds memberId where memberId = :userId"
    )
    List<AuthorizationGroupEntity> findAllForMember(@Param("userId") UUID userId);
}
