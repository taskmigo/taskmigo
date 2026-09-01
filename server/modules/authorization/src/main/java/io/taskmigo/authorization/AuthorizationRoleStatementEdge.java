package io.taskmigo.authorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "authorization_role_statements")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationRoleStatementEdge {

    @Id
    UUID id;

    @Column(name = "role_id", nullable = false)
    UUID roleId;

    @Column(name = "statement_id", nullable = false)
    UUID statementId;

    protected AuthorizationRoleStatementEdge() {}

    AuthorizationRoleStatementEdge(UUID roleId, UUID statementId) {
        this.id = UUID.randomUUID();
        this.roleId = roleId;
        this.statementId = statementId;
    }
}
