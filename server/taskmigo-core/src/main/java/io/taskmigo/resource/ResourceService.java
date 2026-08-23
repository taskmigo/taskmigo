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

/// Coordinates resource lifecycle operations and enforces organization and project membership invariants.
///
/// Validation failures are reported as [ResourceException] values so transport adapters can map domain failures
/// without depending on persistence exceptions.
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

    /// Creates an organization after trimming required text fields and enforcing key uniqueness.
    ///
    /// @param key the external organization key
    /// @param name the organization display name
    /// @return the generated organization id
    /// @throws ResourceException if a required field is blank or the organization key already exists
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

    /// Creates a user in an organization, normalizing the email address before uniqueness is checked.
    ///
    /// @param organizationId the organization that owns the user
    /// @param username the username unique within the persistence model
    /// @param email the email address, normalized to lowercase using [Locale#ROOT]
    /// @param displayName the user-facing display name
    /// @return the generated user id
    /// @throws ResourceException if the organization does not exist, a required field is blank, or username/email
    ///     uniqueness is violated
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

    /// Creates a group owned by an existing organization.
    ///
    /// @param organizationId the organization that owns the group
    /// @param name the group name
    /// @param description the optional group description
    /// @return the generated group id
    /// @throws ResourceException if the organization does not exist or the group name is blank
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

    /// Adds a user to a group only when both resources belong to the same organization.
    ///
    /// @param groupId the target group
    /// @param userId the user to add
    /// @throws ResourceException if either resource does not exist or belongs to a different organization
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

    /// Removes a user from a group; removing a user that is not currently a member is a no-op.
    ///
    /// @param groupId the target group
    /// @param userId the user to remove
    /// @throws ResourceException if the group does not exist
    @Transactional
    public void removeGroupMember(UUID groupId, UUID userId) {
        GroupEntity group = this.group(groupId);
        group.members.removeIf(member -> member.getId().equals(userId));
        this.groups.flush();
    }

    /// Creates an organization-owned role containing only permissions from [PermissionCatalog#ALL].
    ///
    /// @param organizationId the organization that owns the role
    /// @param name the role name
    /// @param description the optional role description
    /// @param permissions permission keys to assign; an absent set creates a role without permissions
    /// @return the generated role id
    /// @throws ResourceException if the organization does not exist, the role name is blank, or an unknown permission
    ///     is requested
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

    /// Creates an active project and enforces project-key uniqueness within its organization.
    ///
    /// @param organizationId the organization that owns the project
    /// @param key the project key unique within the organization
    /// @param name the project display name
    /// @param description the optional project description
    /// @return the generated project id
    /// @throws ResourceException if the organization does not exist, a required field is blank, or the project key
    ///     already exists in the organization
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

    /// Archives a project, making subsequent membership mutations fail as conflicts.
    ///
    /// @param projectId the project to archive
    /// @throws ResourceException if the project does not exist
    @Transactional
    public void archiveProject(UUID projectId) {
        this.project(projectId).status = ProjectStatus.ARCHIVED;
    }

    /// Adds a user or group principal to an active project.
    ///
    /// @param projectId the active project receiving the member
    /// @param principalType either `USER` or `GROUP`, case-insensitively
    /// @param principalId the id of the referenced user or group
    /// @return the generated project-member id
    /// @throws ResourceException if the project is archived, the principal type is invalid, the principal does not
    ///     exist, or the principal is already a member
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

    /// Removes a membership only when it belongs to the specified active project.
    ///
    /// @param projectId the project that must own the membership
    /// @param projectMemberId the membership to remove
    /// @throws ResourceException if the project is archived, the project or member does not exist, or the member
    ///     belongs to another project
    @Transactional
    public void removeProjectMember(UUID projectId, UUID projectMemberId) {
        this.activeProject(projectId);
        ProjectMemberEntity member = this.projectMember(projectMemberId);
        if (!member.project.getId().equals(projectId)) throw notFound("Project Member not found in Project");
        this.projectMembers.delete(member);
        this.projectMembers.flush();
    }

    /// Replaces a project member's roles, allowing only roles owned by the project's organization.
    ///
    /// @param projectId the active project that owns the membership
    /// @param projectMemberId the membership whose roles are replaced
    /// @param roleIds the complete desired role set; an absent set removes all roles
    /// @throws ResourceException if the project is archived, a referenced resource is missing, or a role belongs to
    ///     another organization
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

    /// Computes the union of permissions granted directly to a user and through all of the user's project-member groups.
    ///
    /// Duplicate permissions are collapsed and the returned set is immutable.
    ///
    /// @param projectId the project whose grants are evaluated
    /// @param userId the user whose direct and group-based memberships are evaluated
    /// @return the effective permission keys granted to the user for the project
    /// @throws ResourceException if the project or user does not exist
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
