package io.taskmigo.authorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "authorization_group_statements")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationGroupStatementEdge {

    @Id
    UUID id;

    @Column(name = "group_id", nullable = false)
    UUID groupId;

    @Column(name = "statement_id", nullable = false)
    UUID statementId;

    protected AuthorizationGroupStatementEdge() {}

    AuthorizationGroupStatementEdge(UUID groupId, UUID statementId) {
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.statementId = statementId;
    }
}
