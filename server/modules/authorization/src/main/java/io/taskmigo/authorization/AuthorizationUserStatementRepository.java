package io.taskmigo.authorization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthorizationUserStatementRepository extends JpaRepository<AuthorizationUserStatementAssignment, UUID> {
    List<AuthorizationUserStatementAssignment> findAllByUserId(UUID userId);

    boolean existsByUserIdAndStatementId(UUID userId, UUID statementId);
}
