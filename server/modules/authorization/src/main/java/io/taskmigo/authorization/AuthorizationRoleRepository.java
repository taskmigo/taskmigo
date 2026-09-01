package io.taskmigo.authorization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuthorizationRoleRepository extends JpaRepository<AuthorizationRoleEntity, UUID> {
    Optional<AuthorizationRoleEntity> findByOrganizationIdAndKey(UUID organizationId, String key);

    Optional<AuthorizationRoleEntity> findByOrganizationIdIsNullAndKey(String key);

    @Query(
        "select role from AuthorizationRoleEntity role " +
            "where role.organizationId = :organizationId or role.organizationId is null order by role.key"
    )
    List<AuthorizationRoleEntity> findRelevant(@Param("organizationId") UUID organizationId);

    List<AuthorizationRoleEntity> findAllByOrganizationIdIsNullOrderByKey();
}
