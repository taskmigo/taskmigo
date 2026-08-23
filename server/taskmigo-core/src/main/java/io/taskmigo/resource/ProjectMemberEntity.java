package io.taskmigo.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "project_members")
@SuppressWarnings("NotNullFieldNotInitialized")
class ProjectMemberEntity {

    @Id
    UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    ProjectEntity project;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 16)
    PrincipalType principalType;

    @Column(name = "principal_id", nullable = false)
    UUID principalId;

    @ManyToMany
    @JoinTable(
        name = "project_member_roles",
        joinColumns = @JoinColumn(name = "project_member_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    Set<RoleEntity> roles = new LinkedHashSet<>();

    protected ProjectMemberEntity() {}

    ProjectMemberEntity(UUID id, ProjectEntity project, PrincipalType principalType, UUID principalId) {
        this.id = id;
        this.project = project;
        this.principalType = principalType;
        this.principalId = principalId;
    }

    UUID getId() {
        return this.id;
    }
}
