package io.taskmigo.resource;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceService {

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final GroupRepository groups;
    private final RoleRepository roles;
    private final ProjectRepository projects;
    private final ProjectMemberRepository projectMembers;

    ResourceService(
        OrganizationRepository organizations,
        UserRepository users,
        GroupRepository groups,
        RoleRepository roles,
        ProjectRepository projects,
        ProjectMemberRepository projectMembers
    ) {
        this.organizations = organizations;
        this.users = users;
        this.groups = groups;
        this.roles = roles;
        this.projects = projects;
        this.projectMembers = projectMembers;
    }

    @Transactional
    public UUID createOrganization(@Nullable String key, @Nullable String name) {
        try {
            return this.organizations
                .saveAndFlush(new OrganizationEntity(UUID.randomUUID(), required(key, "key"), required(name, "name")))
                .getId();
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Organization key already exists", exception);
        }
    }

    @Transactional
    public UUID createUser(
        UUID organizationId,
        @Nullable String username,
        @Nullable String email,
        @Nullable String displayName
    ) {
        OrganizationEntity organization = this.organization(organizationId);
        String normalizedEmail = required(email, "email").toLowerCase(Locale.ROOT);
        try {
            return this.users
                .saveAndFlush(
                    new UserEntity(
                        UUID.randomUUID(),
                        organization,
                        required(username, "username"),
                        normalizedEmail,
                        required(displayName, "displayName")
                    )
                )
                .getId();
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Username or normalized email already exists", exception);
        }
    }

    @Transactional
    public UUID createGroup(UUID organizationId, @Nullable String name, @Nullable String description) {
        return this.groups
            .save(
                new GroupEntity(
                    UUID.randomUUID(),
                    this.organization(organizationId),
                    required(name, "name"),
                    description
                )
            )
            .getId();
    }

    @Transactional
    public void addGroupMember(UUID groupId, UUID userId) {
        GroupEntity group = this.group(groupId);
        UserEntity user = this.user(userId);
        if (!group.organization.getId().equals(user.organization.getId())) {
            throw badRequest("A Group can contain only Users from its owning Organization");
        }
        group.members.add(user);
        this.groups.flush();
    }

    @Transactional
    public void removeGroupMember(UUID groupId, UUID userId) {
        GroupEntity group = this.group(groupId);
        group.members.removeIf(member -> member.getId().equals(userId));
        this.groups.flush();
    }

    @Transactional
    public UUID createRole(
        UUID organizationId,
        @Nullable String name,
        @Nullable String description,
        @Nullable Set<String> permissions
    ) {
        Set<String> requested = permissions == null ? Set.of() : Set.copyOf(permissions);
        if (!PermissionCatalog.ALL.containsAll(requested)) {
            Set<String> unknown = new HashSet<>(requested);
            unknown.removeAll(PermissionCatalog.ALL);
            throw badRequest("Unknown permissions: " + unknown);
        }
        return this.roles
            .save(
                new RoleEntity(
                    UUID.randomUUID(),
                    this.organization(organizationId),
                    required(name, "name"),
                    description,
                    requested
                )
            )
            .getId();
    }

    @Transactional
    public UUID createProject(
        UUID organizationId,
        @Nullable String key,
        @Nullable String name,
        @Nullable String description
    ) {
        try {
            return this.projects
                .saveAndFlush(
                    new ProjectEntity(
                        UUID.randomUUID(),
                        this.organization(organizationId),
                        required(key, "key"),
                        required(name, "name"),
                        description
                    )
                )
                .getId();
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Project key already exists in the Organization", exception);
        }
    }

    @Transactional
    public void archiveProject(UUID projectId) {
        this.project(projectId).status = ProjectStatus.ARCHIVED;
    }

    @Transactional
    public UUID addProjectMember(UUID projectId, @Nullable String principalType, UUID principalId) {
        ProjectEntity project = this.activeProject(projectId);
        PrincipalType type = principalType(principalType);
        this.assertPrincipalExists(type, principalId);
        try {
            return this.projectMembers
                .saveAndFlush(new ProjectMemberEntity(UUID.randomUUID(), project, type, principalId))
                .getId();
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Principal is already a Project Member", exception);
        }
    }

    @Transactional
    public void removeProjectMember(UUID projectId, UUID projectMemberId) {
        this.activeProject(projectId);
        ProjectMemberEntity member = this.projectMember(projectMemberId);
        if (!member.project.getId().equals(projectId)) throw notFound("Project Member not found in Project");
        this.projectMembers.delete(member);
        this.projectMembers.flush();
    }

    @Transactional
    public void setProjectMemberRoles(UUID projectId, UUID projectMemberId, @Nullable Set<UUID> roleIds) {
        ProjectEntity project = this.activeProject(projectId);
        ProjectMemberEntity member = this.projectMember(projectMemberId);
        if (!member.project.getId().equals(projectId)) throw notFound("Project Member not found in Project");
        Set<UUID> requestedIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        var requestedRoles = this.roles.findAllByIdIn(requestedIds);
        if (requestedRoles.size() != requestedIds.size()) throw badRequest("One or more Roles do not exist");
        for (RoleEntity role : requestedRoles) {
            if (!role.organization.getId().equals(project.organization.getId())) {
                throw badRequest("A Project Member can receive only Roles owned by the Project Organization");
            }
        }
        member.roles.clear();
        member.roles.addAll(requestedRoles);
        this.projectMembers.flush();
    }

    @Transactional(readOnly = true)
    public Set<String> effectivePermissions(UUID projectId, UUID userId) {
        this.project(projectId);
        this.user(userId);
        Set<String> permissions = new LinkedHashSet<>();
        this.projectMembers
            .findByProjectIdAndPrincipalTypeAndPrincipalId(projectId, PrincipalType.USER, userId)
            .ifPresent(member -> this.collectPermissions(member, permissions));
        var groupIds = this.groups.findIdsContainingUser(userId);
        if (!groupIds.isEmpty()) {
            for (ProjectMemberEntity member : this.projectMembers.findAllByProjectIdAndPrincipalTypeAndPrincipalIdIn(
                projectId,
                PrincipalType.GROUP,
                groupIds
            )) {
                this.collectPermissions(member, permissions);
            }
        }
        return Set.copyOf(permissions);
    }

    private void collectPermissions(ProjectMemberEntity member, Set<String> permissions) {
        for (RoleEntity role : member.roles) permissions.addAll(role.permissions);
    }

    private OrganizationEntity organization(UUID id) {
        return this.organizations.findById(id).orElseThrow(() -> notFound("Organization not found"));
    }

    private UserEntity user(UUID id) {
        return this.users.findById(id).orElseThrow(() -> notFound("User not found"));
    }

    private GroupEntity group(UUID id) {
        return this.groups.findById(id).orElseThrow(() -> notFound("Group not found"));
    }

    private ProjectEntity project(UUID id) {
        return this.projects.findById(id).orElseThrow(() -> notFound("Project not found"));
    }

    private ProjectEntity activeProject(UUID id) {
        ProjectEntity project = this.project(id);
        if (project.status == ProjectStatus.ARCHIVED) throw conflict("Archived Projects are read-only");
        return project;
    }

    private ProjectMemberEntity projectMember(UUID id) {
        return this.projectMembers.findById(id).orElseThrow(() -> notFound("Project Member not found"));
    }

    private void assertPrincipalExists(PrincipalType type, UUID principalId) {
        boolean exists = switch (type) {
            case USER -> this.users.existsById(principalId);
            case GROUP -> this.groups.existsById(principalId);
        };
        if (!exists) throw badRequest("Project Member principal does not exist");
    }

    private static PrincipalType principalType(@Nullable String value) {
        try {
            return PrincipalType.valueOf(required(value, "principalType").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("principalType must be USER or GROUP");
        }
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) throw badRequest(field + " is required");
        return value.trim();
    }

    private static ResourceException badRequest(String message) {
        return new ResourceException(ResourceException.Type.BAD_REQUEST, message);
    }

    private static ResourceException conflict(String message) {
        return new ResourceException(ResourceException.Type.CONFLICT, message);
    }

    private static ResourceException conflict(String message, Throwable cause) {
        return new ResourceException(ResourceException.Type.CONFLICT, message, cause);
    }

    private static ResourceException notFound(String message) {
        return new ResourceException(ResourceException.Type.NOT_FOUND, message);
    }
}
