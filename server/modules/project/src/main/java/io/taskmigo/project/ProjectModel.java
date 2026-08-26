package io.taskmigo.project;

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

@Entity
@Table(name = "projects")
@SuppressWarnings({ "CanBeFinal", "ClassNameDiffersFromFileName", "NotNullFieldNotInitialized" })
class ProjectEntity {

    @Id
    UUID id;

    @Column(name = "organization_id", nullable = false)
    UUID organizationId;

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

    ProjectEntity(UUID id, UUID organizationId, String key, String name, @Nullable String description) {
        this.id = id;
        this.organizationId = organizationId;
        this.key = key;
        this.name = name;
        this.description = description;
        this.status = ProjectStatus.ACTIVE;
    }
}

@Entity
@Table(name = "project_members")
@SuppressWarnings({ "CanBeFinal", "ClassNameDiffersFromFileName", "NotNullFieldNotInitialized" })
class ProjectMemberEntity {

    @Id
    UUID id;

    @Column(name = "project_id", nullable = false)
    UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 16)
    PrincipalType principalType;

    @Column(name = "principal_id", nullable = false)
    UUID principalId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_member_roles", joinColumns = @JoinColumn(name = "project_member_id"))
    @Column(name = "role_id", nullable = false)
    Set<UUID> roleIds = new LinkedHashSet<>();

    protected ProjectMemberEntity() {}

    ProjectMemberEntity(UUID id, UUID projectId, PrincipalType principalType, UUID principalId) {
        this.id = id;
        this.projectId = projectId;
        this.principalType = principalType;
        this.principalId = principalId;
    }
}

@SuppressWarnings("ClassNameDiffersFromFileName")
enum ProjectStatus {
    ACTIVE,
    ARCHIVED,
}

@SuppressWarnings("ClassNameDiffersFromFileName")
enum PrincipalType {
    USER,
    GROUP,
}
