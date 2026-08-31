package io.taskmigo.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class UserRoleAssignmentEntity {

    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "role_id", nullable = false)
    UUID roleId;

    protected UserRoleAssignmentEntity() {}

    UserRoleAssignmentEntity(UUID id, UUID userId, UUID roleId) {
        this.id = id;
        this.userId = userId;
        this.roleId = roleId;
    }
}
