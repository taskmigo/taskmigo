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
            return organizations
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
        OrganizationEntity organization = organization(organizationId);
        String normalizedEmail = required(email, "email").toLowerCase(Locale.ROOT);
        try {
            return users
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
        return groups
            .save(new GroupEntity(UUID.randomUUID(), organization(organizationId), required(name, "name"), description))
            .getId();
    }

    @Transactional
    public void addGroupMember(UUID groupId, UUID userId) {
        GroupEntity group = group(groupId);
        UserEntity user = user(userId);
        if (!group.organization.getId().equals(user.organization.getId())) {
            throw badRequest("A Group can contain only Users from its owning Organization");
        }
        group.members.add(user);
        groups.flush();
    }

    @Transactional
    public void removeGroupMember(UUID groupId, UUID userId) {
        GroupEntity group = group(groupId);
        group.members.removeIf(member -> member.getId().equals(userId));
        groups.flush();
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
        return roles
            .save(
                new RoleEntity(
                    UUID.randomUUID(),
                    organization(organizationId),
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
            return projects
                .saveAndFlush(
                    new ProjectEntity(
                        UUID.randomUUID(),
                        organization(organizationId),
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
        project(projectId).status = ProjectStatus.ARCHIVED;
    }

    @Transactional
    public UUID addProjectMember(UUID projectId, @Nullable String principalType, UUID principalId) {
        ProjectEntity project = activeProject(projectId);
        PrincipalType type = principalType(principalType);
        assertPrincipalExists(type, principalId);
        try {
            return projectMembers
                .saveAndFlush(new ProjectMemberEntity(UUID.randomUUID(), project, type, principalId))
                .getId();
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Principal is already a Project Member", exception);
        }
    }

    @Transactional
    public void removeProjectMember(UUID projectId, UUID projectMemberId) {
        activeProject(projectId);
        ProjectMemberEntity member = projectMember(projectMemberId);
        if (!member.project.getId().equals(projectId)) throw notFound("Project Member not found in Project");
        projectMembers.delete(member);
        projectMembers.flush();
    }

    @Transactional
    public void setProjectMemberRoles(UUID projectId, UUID projectMemberId, @Nullable Set<UUID> roleIds) {
        ProjectEntity project = activeProject(projectId);
        ProjectMemberEntity member = projectMember(projectMemberId);
        if (!member.project.getId().equals(projectId)) throw notFound("Project Member not found in Project");
        Set<UUID> requestedIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        var requestedRoles = roles.findAllByIdIn(requestedIds);
        if (requestedRoles.size() != requestedIds.size()) throw badRequest("One or more Roles do not exist");
        for (RoleEntity role : requestedRoles) {
            if (!role.organization.getId().equals(project.organization.getId())) {
                throw badRequest("A Project Member can receive only Roles owned by the Project Organization");
            }
        }
        member.roles.clear();
        member.roles.addAll(requestedRoles);
        projectMembers.flush();
    }

    @Transactional(readOnly = true)
    public Set<String> effectivePermissions(UUID projectId, UUID userId) {
        project(projectId);
        user(userId);
        Set<String> permissions = new LinkedHashSet<>();
        projectMembers
            .findByProjectIdAndPrincipalTypeAndPrincipalId(projectId, PrincipalType.USER, userId)
            .ifPresent(member -> collectPermissions(member, permissions));
        var groupIds = groups.findIdsContainingUser(userId);
        if (!groupIds.isEmpty()) {
            for (ProjectMemberEntity member : projectMembers.findAllByProjectIdAndPrincipalTypeAndPrincipalIdIn(
                projectId,
                PrincipalType.GROUP,
                groupIds
            )) {
                collectPermissions(member, permissions);
            }
        }
        return Set.copyOf(permissions);
    }

    private void collectPermissions(ProjectMemberEntity member, Set<String> permissions) {
        for (RoleEntity role : member.roles) permissions.addAll(role.permissions);
    }

    private OrganizationEntity organization(UUID id) {
        return organizations.findById(id).orElseThrow(() -> notFound("Organization not found"));
    }

    private UserEntity user(UUID id) {
        return users.findById(id).orElseThrow(() -> notFound("User not found"));
    }

    private GroupEntity group(UUID id) {
        return groups.findById(id).orElseThrow(() -> notFound("Group not found"));
    }

    private ProjectEntity project(UUID id) {
        return projects.findById(id).orElseThrow(() -> notFound("Project not found"));
    }

    private ProjectEntity activeProject(UUID id) {
        ProjectEntity project = project(id);
        if (project.status == ProjectStatus.ARCHIVED) throw conflict("Archived Projects are read-only");
        return project;
    }

    private ProjectMemberEntity projectMember(UUID id) {
        return projectMembers.findById(id).orElseThrow(() -> notFound("Project Member not found"));
    }

    private void assertPrincipalExists(PrincipalType type, UUID principalId) {
        boolean exists = switch (type) {
            case USER -> users.existsById(principalId);
            case GROUP -> groups.existsById(principalId);
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
