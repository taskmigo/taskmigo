package io.taskmigo.authorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "authorization_user_roles")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationUserRoleAssignment {

    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "role_id", nullable = false)
    UUID roleId;

    protected AuthorizationUserRoleAssignment() {}

    AuthorizationUserRoleAssignment(UUID userId, UUID roleId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.roleId = roleId;
    }
}
