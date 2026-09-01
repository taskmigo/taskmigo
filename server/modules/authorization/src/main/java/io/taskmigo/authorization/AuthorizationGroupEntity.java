package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationResource.Origin;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity(name = "AuthorizationGroupEntity")
@Table(name = "groups")
@SuppressWarnings({ "CanBeFinal", "NotNullFieldNotInitialized" })
class AuthorizationGroupEntity {

    @Id
    UUID id;

    @Nullable
    @Column(name = "organization_id")
    UUID organizationId;

    @Column(name = "group_key", nullable = false, length = 128)
    String key;

    @Column(nullable = false, length = 200)
    String name;

    @Nullable
    @Column(length = 1000)
    String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_origin", nullable = false, length = 16)
    Origin origin;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "group_members", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "user_id", nullable = false)
    Set<UUID> memberIds;

    protected AuthorizationGroupEntity() {}

    AuthorizationGroupEntity(
        UUID id,
        @Nullable UUID organizationId,
        AuthorizationResource.Group resource,
        Origin origin
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.key = resource.key();
        this.name = resource.name() == null || resource.name().isBlank() ? resource.key() : resource.name();
        this.description = resource.description();
        this.origin = origin;
        this.memberIds = new LinkedHashSet<>();
    }

    void replace(AuthorizationResource.Group resource, Origin origin) {
        this.key = resource.key();
        this.name = resource.name() == null || resource.name().isBlank() ? resource.key() : resource.name();
        this.description = resource.description();
        this.origin = origin;
    }
}
