package io.taskmigo.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "groups")
@SuppressWarnings("NotNullFieldNotInitialized")
class GroupEntity {

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

    @ManyToMany
    @JoinTable(
        name = "group_members",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    Set<UserEntity> members = new LinkedHashSet<>();

    protected GroupEntity() {}

    GroupEntity(UUID id, OrganizationEntity organization, String name, @Nullable String description) {
        this.id = id;
        this.organization = organization;
        this.name = name;
        this.description = description;
    }

    UUID getId() {
        return this.id;
    }
}
