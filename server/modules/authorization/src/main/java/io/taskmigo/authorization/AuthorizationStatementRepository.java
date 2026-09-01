package io.taskmigo.authorization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuthorizationStatementRepository extends JpaRepository<AuthorizationStatementEntity, UUID> {
    Optional<AuthorizationStatementEntity> findByOrganizationIdAndKey(UUID organizationId, String key);

    Optional<AuthorizationStatementEntity> findByOrganizationIdIsNullAndKey(String key);

    @Query(
        "select statement from AuthorizationStatementEntity statement " +
            "where statement.organizationId = :organizationId or statement.organizationId is null order by statement.key"
    )
    List<AuthorizationStatementEntity> findRelevant(@Param("organizationId") UUID organizationId);

    List<AuthorizationStatementEntity> findAllByOrganizationIdIsNullOrderByKey();
}
