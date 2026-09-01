package io.taskmigo.authorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "authorization_role_inheritance")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationRoleInheritanceEdge {

    @Id
    UUID id;

    @Column(name = "role_id", nullable = false)
    UUID roleId;

    @Column(name = "included_role_id", nullable = false)
    UUID includedRoleId;

    protected AuthorizationRoleInheritanceEdge() {}

    AuthorizationRoleInheritanceEdge(UUID roleId, UUID includedRoleId) {
        this.id = UUID.randomUUID();
        this.roleId = roleId;
        this.includedRoleId = includedRoleId;
    }
}
