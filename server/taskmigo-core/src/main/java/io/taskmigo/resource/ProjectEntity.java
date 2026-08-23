package io.taskmigo.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "projects")
@SuppressWarnings("NotNullFieldNotInitialized")
class ProjectEntity {

    @Id
    UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    OrganizationEntity organization;

    @Column(name = "project_key", nullable = false, length = 64)
    String key;

    @Column(nullable = false, length = 200)
    String name;

    @Column(length = 2000)
    @Nullable
    String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    ProjectStatus status;

    protected ProjectEntity() {}

    ProjectEntity(UUID id, OrganizationEntity organization, String key, String name, @Nullable String description) {
        this.id = id;
        this.organization = organization;
        this.key = key;
        this.name = name;
        this.description = description;
        this.status = ProjectStatus.ACTIVE;
    }

    UUID getId() {
        return this.id;
    }
}
