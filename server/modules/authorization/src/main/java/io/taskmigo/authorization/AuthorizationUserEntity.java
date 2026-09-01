package io.taskmigo.authorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity(name = "AuthorizationUserEntity")
@Table(name = "users")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationUserEntity {

    @Id
    UUID id;

    @Nullable
    @Column(name = "organization_id")
    UUID organizationId;

    protected AuthorizationUserEntity() {}
}
