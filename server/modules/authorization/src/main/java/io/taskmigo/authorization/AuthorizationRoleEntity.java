package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationResource.Origin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity(name = "AuthorizationRoleEntity")
@Table(name = "roles")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationRoleEntity {

    @Id
    UUID id;

    @Nullable
    @Column(name = "organization_id")
    UUID organizationId;

    @Column(name = "role_key", nullable = false, length = 128)
    String key;

    @Column(nullable = false, length = 200)
    String name;

    @Nullable
    @Column(length = 1000)
    String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_origin", nullable = false, length = 16)
    Origin origin;

    protected AuthorizationRoleEntity() {}

    AuthorizationRoleEntity(
        UUID id,
        @Nullable UUID organizationId,
        AuthorizationResource.Role resource,
        Origin origin
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.key = resource.key();
        this.name = resource.name() == null || resource.name().isBlank() ? resource.key() : resource.name();
        this.description = resource.description();
        this.origin = origin;
    }

    void replace(AuthorizationResource.Role resource, Origin origin) {
        this.key = resource.key();
        this.name = resource.name() == null || resource.name().isBlank() ? resource.key() : resource.name();
        this.description = resource.description();
        this.origin = origin;
    }
}
