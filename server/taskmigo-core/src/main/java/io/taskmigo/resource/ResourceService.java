package io.taskmigo.resource;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

    ResourceService(
        OrganizationRepository organizations,
        UserRepository users,
        GroupRepository groups,
        RoleRepository roles,
        ProjectRepository projects,
        ProjectMemberRepository projectMembers,
        ApplicationEventPublisher events
    ) {
        this.organizations = organizations;
        this.users = users;
        this.groups = groups;
        this.roles = roles;
        this.projects = projects;
        this.projectMembers = projectMembers;
        this.events = events;
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
    @Transactional
    public UUID createProject(
        UUID organizationId,
        @Nullable String key,
        @Nullable String name,
        @Nullable String description
    ) {
        return this.createProject(organizationId, key, name, description, systemActor());
    }

    /// Creates an active project and records the actor responsible for the change.
    @Transactional
    public UUID createProject(
        UUID organizationId,
        @Nullable String key,
        @Nullable String name,
        @Nullable String description,
        ProjectChanged.Actor actor
    ) {
        try {
            ProjectEntity project = this.projects.saveAndFlush(
                new ProjectEntity(
                    UUID.randomUUID(),
                    this.organization(organizationId),
                    required(key, "key"),
                    required(name, "name"),
                    description
                )
            );
            this.publish(
                project,
                ProjectChanged.Action.PROJECT_CREATED,
                actor,
                this.projectTarget(project),
                List.of(),
                projectSnapshot(project)
            );
            return project.getId();
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Project key already exists in the Organization", exception);
        }
    }

    /// Archives a project, making subsequent membership mutations fail as conflicts.
    @Transactional
    public void archiveProject(UUID projectId) {
        this.archiveProject(projectId, systemActor());
    }

    /// Archives a project and records the actor responsible for the change.
    @Transactional
    public void archiveProject(UUID projectId, ProjectChanged.Actor actor) {
        ProjectEntity project = this.project(projectId);
        project.status = ProjectStatus.ARCHIVED;
        this.publish(
            project,
            ProjectChanged.Action.PROJECT_ARCHIVED,
            actor,
            this.projectTarget(project),
            List.of(new ProjectChanged.Change("status", ProjectStatus.ACTIVE.name(), ProjectStatus.ARCHIVED.name())),
            Map.of()
        );
    }

    /// Adds a user or group principal to an active project.
    @Transactional
    public UUID addProjectMember(UUID projectId, @Nullable String principalType, UUID principalId) {
        return this.addProjectMember(projectId, principalType, principalId, systemActor());
    }

    /// Adds a user or group principal to an active project and records the actor responsible for the change.
    @Transactional
    public UUID addProjectMember(
        UUID projectId,
        @Nullable String principalType,
        UUID principalId,
        ProjectChanged.Actor actor
    ) {
        ProjectEntity project = this.activeProject(projectId);
        PrincipalType type = principalType(principalType);
        ProjectChanged.Target target = this.principalTarget(type, principalId);
        try {
            ProjectMemberEntity member = this.projectMembers.saveAndFlush(
                new ProjectMemberEntity(UUID.randomUUID(), project, type, principalId)
            );
            ProjectChanged.Action action = isSelf(actor, target)
                ? ProjectChanged.Action.MEMBER_JOINED
                : ProjectChanged.Action.MEMBER_ADDED;
            this.publish(project, action, actor, target, List.of(), Map.of("membershipId", member.getId()));
            return member.getId();
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Principal is already a Project Member", exception);
        }
    }

    /// Removes a membership only when it belongs to the specified active project.
    @Transactional
    public void removeProjectMember(UUID projectId, UUID projectMemberId) {
        this.removeProjectMember(projectId, projectMemberId, systemActor());
    }

    /// Removes a project membership and distinguishes self-leave from removal by another actor.
    @Transactional
    public void removeProjectMember(UUID projectId, UUID projectMemberId, ProjectChanged.Actor actor) {
        ProjectEntity project = this.activeProject(projectId);
        ProjectMemberEntity member = this.projectMember(projectMemberId);
        if (!member.project.getId().equals(projectId)) throw notFound("Project Member not found in Project");
        ProjectChanged.Target target = this.principalTarget(member.principalType, member.principalId);
        this.projectMembers.delete(member);
        this.projectMembers.flush();
        ProjectChanged.Action action = isSelf(actor, target)
            ? ProjectChanged.Action.MEMBER_LEFT
            : ProjectChanged.Action.MEMBER_REMOVED;
        this.publish(project, action, actor, target, List.of(), Map.of("membershipId", projectMemberId));
    }

    /// Replaces a project member's roles, allowing only roles owned by the project's organization.
    @Transactional
    public void setProjectMemberRoles(UUID projectId, UUID projectMemberId, @Nullable Set<UUID> roleIds) {
        this.setProjectMemberRoles(projectId, projectMemberId, roleIds, systemActor());
    }

    /// Replaces a project member's roles and records before/after role snapshots for audit presentation.
    @Transactional
    public void setProjectMemberRoles(
        UUID projectId,
        UUID projectMemberId,
        @Nullable Set<UUID> roleIds,
        ProjectChanged.Actor actor
    ) {
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
        List<Map<String, Object>> before = roleSnapshots(member.roles);
        member.roles.clear();
        member.roles.addAll(requestedRoles);
        this.projectMembers.flush();
        List<Map<String, Object>> after = roleSnapshots(member.roles);
        if (!before.equals(after)) {
            this.publish(
                project,
                ProjectChanged.Action.MEMBER_ROLES_CHANGED,
                actor,
                this.principalTarget(member.principalType, member.principalId),
                List.of(new ProjectChanged.Change("roles", before, after)),
                Map.of("membershipId", projectMemberId)
            );
        }
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

    private void publish(
        ProjectEntity project,
        ProjectChanged.Action action,
        ProjectChanged.Actor actor,
        ProjectChanged.@Nullable Target target,
        List<ProjectChanged.Change> changes,
        Map<String, Object> data
    ) {
        this.events.publishEvent(
            new ProjectChanged(UUID.randomUUID(), project.getId(), action, actor, target, changes, data, Instant.now())
        );
    }

    private ProjectChanged.Target projectTarget(ProjectEntity project) {
        return new ProjectChanged.Target(ProjectChanged.TargetType.PROJECT, project.getId().toString(), project.name);
    }

    private ProjectChanged.Target principalTarget(PrincipalType type, UUID id) {
        return switch (type) {
            case USER -> {
                UserEntity user = this.user(id);
                yield new ProjectChanged.Target(ProjectChanged.TargetType.USER, id.toString(), user.displayName);
            }
            case GROUP -> {
                GroupEntity group = this.group(id);
                yield new ProjectChanged.Target(ProjectChanged.TargetType.GROUP, id.toString(), group.name);
            }
        };
    }

    private static boolean isSelf(ProjectChanged.Actor actor, ProjectChanged.Target target) {
        return (
            actor.type() == ProjectChanged.ActorType.USER &&
            target.type() == ProjectChanged.TargetType.USER &&
            actor.id().equals(target.id())
        );
    }

    private static Map<String, Object> projectSnapshot(ProjectEntity project) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("key", project.key);
        snapshot.put("name", project.name);
        if (project.description != null) snapshot.put("description", project.description);
        snapshot.put("status", project.status.name());
        return Map.copyOf(snapshot);
    }

    private static List<Map<String, Object>> roleSnapshots(Set<RoleEntity> roles) {
        return roles
            .stream()
            .sorted((left, right) -> left.getId().compareTo(right.getId()))
            .map(role -> Map.<String, Object>of("id", role.getId(), "name", role.name))
            .toList();
    }

    private static ProjectChanged.Actor systemActor() {
        return new ProjectChanged.Actor(ProjectChanged.ActorType.SYSTEM, "taskmigo", "Taskmigo System");
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
