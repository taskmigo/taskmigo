package io.taskmigo.resource;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "roles")
@SuppressWarnings("NotNullFieldNotInitialized")
class RoleEntity {

    @Id
    UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    OrganizationEntity organization;

    @Column(nullable = false, length = 200)
    String name;

    @Column(length = 1000)
    @Nullable
    String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_key", nullable = false, length = 100)
    Set<String> permissions = new LinkedHashSet<>();

    protected RoleEntity() {}

    RoleEntity(
        UUID id,
        OrganizationEntity organization,
        String name,
        @Nullable String description,
        Set<String> permissions
    ) {
        this.id = id;
        this.organization = organization;
        this.name = name;
        this.description = description;
        this.permissions.addAll(permissions);
    }

    UUID getId() {
        return this.id;
    }
}
