package io.taskmigo.resource;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

enum UserStatus { ACTIVE, SUSPENDED, DISABLED }
enum ProjectStatus { ACTIVE, ARCHIVED }
enum PrincipalType { USER, GROUP }

@Entity
@Table(name = "organizations")
class OrganizationEntity {
    @Id UUID id;
    @Column(name = "organization_key", nullable = false, length = 64) String key;
    @Column(nullable = false, length = 200) String name;
    protected OrganizationEntity() {}
    OrganizationEntity(UUID id, String key, String name) { this.id = id; this.key = key; this.name = name; }
    UUID getId() { return id; }
}

@Entity
@Table(name = "users")
class UserEntity {
    @Id UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "organization_id", nullable = false) OrganizationEntity organization;
    @Column(nullable = false, length = 100) String username;
    @Column(name = "normalized_email", nullable = false, length = 320) String normalizedEmail;
    @Column(name = "display_name", nullable = false, length = 200) String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) UserStatus status;
    protected UserEntity() {}
    UserEntity(UUID id, OrganizationEntity organization, String username, String normalizedEmail, String displayName) {
        this.id = id; this.organization = organization; this.username = username; this.normalizedEmail = normalizedEmail;
        this.displayName = displayName; this.status = UserStatus.ACTIVE;
    }
    UUID getId() { return id; }
}

@Entity
@Table(name = "groups")
class GroupEntity {
    @Id UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "organization_id", nullable = false) OrganizationEntity organization;
    @Column(nullable = false, length = 200) String name;
    @Column(length = 1000) String description;
    @ManyToMany
    @JoinTable(name = "group_members", joinColumns = @JoinColumn(name = "group_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    Set<UserEntity> members = new LinkedHashSet<>();
    protected GroupEntity() {}
    GroupEntity(UUID id, OrganizationEntity organization, String name, String description) {
        this.id = id; this.organization = organization; this.name = name; this.description = description;
    }
    UUID getId() { return id; }
}

@Entity
@Table(name = "roles")
class RoleEntity {
    @Id UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "organization_id", nullable = false) OrganizationEntity organization;
    @Column(nullable = false, length = 200) String name;
    @Column(length = 1000) String description;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_key", nullable = false, length = 100)
    Set<String> permissions = new LinkedHashSet<>();
    protected RoleEntity() {}
    RoleEntity(UUID id, OrganizationEntity organization, String name, String description, Set<String> permissions) {
        this.id = id; this.organization = organization; this.name = name; this.description = description; this.permissions.addAll(permissions);
    }
    UUID getId() { return id; }
}

@Entity
@Table(name = "projects")
class ProjectEntity {
    @Id UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "organization_id", nullable = false) OrganizationEntity organization;
    @Column(name = "project_key", nullable = false, length = 64) String key;
    @Column(nullable = false, length = 200) String name;
    @Column(length = 2000) String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) ProjectStatus status;
    protected ProjectEntity() {}
    ProjectEntity(UUID id, OrganizationEntity organization, String key, String name, String description) {
        this.id = id; this.organization = organization; this.key = key; this.name = name; this.description = description; this.status = ProjectStatus.ACTIVE;
    }
    UUID getId() { return id; }
}

@Entity
@Table(name = "project_members")
class ProjectMemberEntity {
    @Id UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) ProjectEntity project;
    @Enumerated(EnumType.STRING) @Column(name = "principal_type", nullable = false, length = 16) PrincipalType principalType;
    @Column(name = "principal_id", nullable = false) UUID principalId;
    @ManyToMany
    @JoinTable(name = "project_member_roles", joinColumns = @JoinColumn(name = "project_member_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    Set<RoleEntity> roles = new LinkedHashSet<>();
    protected ProjectMemberEntity() {}
    ProjectMemberEntity(UUID id, ProjectEntity project, PrincipalType principalType, UUID principalId) {
        this.id = id; this.project = project; this.principalType = principalType; this.principalId = principalId;
    }
    UUID getId() { return id; }
}
