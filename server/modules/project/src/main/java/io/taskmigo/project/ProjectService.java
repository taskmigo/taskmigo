package io.taskmigo.project;

import io.taskmigo.access.AccessService;
import io.taskmigo.access.PermissionCatalog;
import io.taskmigo.group.GroupService;
import io.taskmigo.organization.OrganizationService;
import io.taskmigo.user.UserService;
import java.time.Instant;
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

/// Manages project lifecycle, membership, role assignment, and effective project permissions.
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final OrganizationService organizations;
    private final UserService users;
    private final GroupService groups;
    private final AccessService access;
    private final ApplicationEventPublisher events;

    ProjectService(
        ProjectRepository projects,
        ProjectMemberRepository members,
        OrganizationService organizations,
        UserService users,
        GroupService groups,
        AccessService access,
        ApplicationEventPublisher events
    ) {
        this.projects = projects;
        this.members = members;
        this.organizations = organizations;
        this.users = users;
        this.groups = groups;
        this.access = access;
        this.events = events;
    }

    @Transactional
    public UUID create(UUID organizationId, @Nullable String key, @Nullable String name, @Nullable String description) {
        return this.create(organizationId, key, name, description, systemActor());
    }

    @Transactional
    public UUID create(
        UUID organizationId,
        @Nullable String key,
        @Nullable String name,
        @Nullable String description,
        ProjectChanged.Actor actor
    ) {
        this.organizations.require(organizationId);
        try {
            ProjectEntity project = new ProjectEntity(
                UUID.randomUUID(),
                organizationId,
                required(key, "key"),
                required(name, "name"),
                description
            );
            this.projects.saveAndFlush(project);
            this.publish(
                project,
                ProjectChanged.Action.PROJECT_CREATED,
                actor,
                projectTarget(project),
                List.of(),
                projectSnapshot(project)
            );
            return project.id;
        } catch (DataIntegrityViolationException exception) {
            throw new ProjectException(
                ProjectException.Type.CONFLICT,
                "Project key already exists in the Organization",
                exception
            );
        }
    }

    @Transactional
    public void archive(UUID projectId) {
        this.archive(projectId, systemActor());
    }

    @Transactional
    public void archive(UUID projectId, ProjectChanged.Actor actor) {
        ProjectEntity project = this.project(projectId);
        project.status = ProjectStatus.ARCHIVED;
        this.publish(
            project,
            ProjectChanged.Action.PROJECT_ARCHIVED,
            actor,
            projectTarget(project),
            List.of(new ProjectChanged.Change("status", ProjectStatus.ACTIVE.name(), ProjectStatus.ARCHIVED.name())),
            Map.of()
        );
    }

    @Transactional
    public UUID addMember(UUID projectId, @Nullable String principalType, UUID principalId) {
        return this.addMember(projectId, principalType, principalId, systemActor());
    }

    @Transactional
    public UUID addMember(
        UUID projectId,
        @Nullable String principalType,
        UUID principalId,
        ProjectChanged.Actor actor
    ) {
        ProjectEntity project = this.activeProject(projectId);
        PrincipalType type = principalType(principalType);
        ProjectChanged.Target target = this.principalTarget(type, principalId);
        try {
            ProjectMemberEntity member = new ProjectMemberEntity(UUID.randomUUID(), project.id, type, principalId);
            this.members.saveAndFlush(member);
            ProjectChanged.Action action = isSelf(actor, target)
                ? ProjectChanged.Action.MEMBER_JOINED
                : ProjectChanged.Action.MEMBER_ADDED;
            this.publish(project, action, actor, target, List.of(), Map.of("membershipId", member.id));
            return member.id;
        } catch (DataIntegrityViolationException exception) {
            throw new ProjectException(
                ProjectException.Type.CONFLICT,
                "Principal is already a Project Member",
                exception
            );
        }
    }

    @Transactional
    public void removeMember(UUID projectId, UUID projectMemberId) {
        this.removeMember(projectId, projectMemberId, systemActor());
    }

    @Transactional
    public void removeMember(UUID projectId, UUID projectMemberId, ProjectChanged.Actor actor) {
        ProjectEntity project = this.activeProject(projectId);
        ProjectMemberEntity member = this.member(projectMemberId);
        if (!member.projectId.equals(projectId)) throw notFound("Project Member not found in Project");
        ProjectChanged.Target target = this.principalTarget(member.principalType, member.principalId);
        this.members.delete(member);
        this.members.flush();
        ProjectChanged.Action action = isSelf(actor, target)
            ? ProjectChanged.Action.MEMBER_LEFT
            : ProjectChanged.Action.MEMBER_REMOVED;
        this.publish(project, action, actor, target, List.of(), Map.of("membershipId", projectMemberId));
    }

    @Transactional
    public void setMemberRoles(UUID projectId, UUID projectMemberId, @Nullable Set<UUID> roleIds) {
        this.setMemberRoles(projectId, projectMemberId, roleIds, systemActor());
    }

    @Transactional
    public void setMemberRoles(
        UUID projectId,
        UUID projectMemberId,
        @Nullable Set<UUID> roleIds,
        ProjectChanged.Actor actor
    ) {
        ProjectEntity project = this.activeProject(projectId);
        ProjectMemberEntity member = this.member(projectMemberId);
        if (!member.projectId.equals(projectId)) throw notFound("Project Member not found in Project");
        Set<UUID> requestedIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        List<AccessService.RoleInfo> requestedRoles = this.access.requireRoles(requestedIds);
        for (var role : requestedRoles) {
            if (!role.organizationId().equals(project.organizationId)) {
                throw badRequest("A Project Member can receive only Roles owned by the Project Organization");
            }
        }
        List<Map<String, Object>> before = roleSnapshots(this.access.requireRoles(member.roleIds));
        member.roleIds.clear();
        member.roleIds.addAll(requestedIds);
        this.members.flush();
        List<Map<String, Object>> after = roleSnapshots(requestedRoles);
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

    @Transactional(readOnly = true)
    public Set<String> effectivePermissions(UUID projectId, UUID userId) {
        this.project(projectId);
        UserService.UserInfo user = this.users.require(userId);
        if (user.system()) return PermissionCatalog.ALL;

        Set<String> permissions = new LinkedHashSet<>();
        this.members
            .findByProjectIdAndPrincipalTypeAndPrincipalId(projectId, PrincipalType.USER, userId)
            .ifPresent(member -> this.collectPermissions(member, permissions));
        List<UUID> groupIds = this.groups.groupsForUser(userId);
        if (!groupIds.isEmpty()) {
            for (ProjectMemberEntity member : this.members.findAllByProjectIdAndPrincipalTypeAndPrincipalIdIn(
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
        for (var role : this.access.requireRoles(member.roleIds)) permissions.addAll(role.permissions());
    }

    private ProjectChanged.Target principalTarget(PrincipalType type, UUID id) {
        return switch (type) {
            case USER -> new ProjectChanged.Target(
                ProjectChanged.TargetType.USER,
                id.toString(),
                this.users.require(id).displayName()
            );
            case GROUP -> new ProjectChanged.Target(
                ProjectChanged.TargetType.GROUP,
                id.toString(),
                this.groups.require(id).name()
            );
        };
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
            new ProjectChanged(UUID.randomUUID(), project.id, action, actor, target, changes, data, Instant.now())
        );
    }

    private static ProjectChanged.Target projectTarget(ProjectEntity project) {
        return new ProjectChanged.Target(ProjectChanged.TargetType.PROJECT, project.id.toString(), project.name);
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

    private static List<Map<String, Object>> roleSnapshots(List<AccessService.RoleInfo> roles) {
        return roles
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .map(role -> Map.<String, Object>of("id", role.id(), "name", role.name()))
            .toList();
    }

    private ProjectEntity project(UUID id) {
        return this.projects.findById(id).orElseThrow(() -> notFound("Project not found"));
    }

    private ProjectEntity activeProject(UUID id) {
        ProjectEntity project = this.project(id);
        if (project.status == ProjectStatus.ARCHIVED) throw new ProjectException(
            ProjectException.Type.CONFLICT,
            "Archived Projects are read-only"
        );
        return project;
    }

    private ProjectMemberEntity member(UUID id) {
        return this.members.findById(id).orElseThrow(() -> notFound("Project Member not found"));
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

    private static ProjectChanged.Actor systemActor() {
        return new ProjectChanged.Actor(ProjectChanged.ActorType.SYSTEM, "taskmigo", "Taskmigo System");
    }

    private static ProjectException badRequest(String message) {
        return new ProjectException(ProjectException.Type.BAD_REQUEST, message);
    }

    private static ProjectException notFound(String message) {
        return new ProjectException(ProjectException.Type.NOT_FOUND, message);
    }
}
