package io.taskmigo.authorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "authorization_user_statements")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationUserStatementAssignment {

    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "statement_id", nullable = false)
    UUID statementId;

    protected AuthorizationUserStatementAssignment() {}

    AuthorizationUserStatementAssignment(UUID userId, UUID statementId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.statementId = statementId;
    }
}
