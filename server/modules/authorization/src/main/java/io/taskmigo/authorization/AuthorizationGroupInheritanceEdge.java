package io.taskmigo.authorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "authorization_group_inheritance")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationGroupInheritanceEdge {

    @Id
    UUID id;

    @Column(name = "group_id", nullable = false)
    UUID groupId;

    @Column(name = "included_group_id", nullable = false)
    UUID includedGroupId;

    protected AuthorizationGroupInheritanceEdge() {}

    AuthorizationGroupInheritanceEdge(UUID groupId, UUID includedGroupId) {
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.includedGroupId = includedGroupId;
    }
}
