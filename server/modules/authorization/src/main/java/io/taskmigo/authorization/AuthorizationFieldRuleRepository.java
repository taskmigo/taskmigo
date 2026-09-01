package io.taskmigo.authorization;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthorizationFieldRuleRepository extends JpaRepository<AuthorizationFieldRuleEntity, UUID> {
    List<AuthorizationFieldRuleEntity> findAllByStatementIdIn(Collection<UUID> statementIds);

    void deleteAllByStatementId(UUID statementId);
}
