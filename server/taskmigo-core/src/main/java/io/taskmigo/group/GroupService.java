package io.taskmigo.group;

import io.taskmigo.organization.OrganizationService;
import io.taskmigo.user.UserService;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages groups and organization-scoped group membership.
@Service
public class GroupService {

    private final GroupRepository groups;
    private final OrganizationService organizations;
    private final UserService users;

    GroupService(GroupRepository groups, OrganizationService organizations, UserService users) {
        this.groups = groups;
        this.organizations = organizations;
        this.users = users;
    }

    @Transactional
    public UUID create(UUID organizationId, @Nullable String name, @Nullable String description) {
        this.organizations.require(organizationId);
        UUID id = UUID.randomUUID();
        this.groups.save(new GroupEntity(id, organizationId, required(name, "name"), description));
        return id;
    }

    @Transactional
    public void addMember(UUID groupId, UUID userId) {
        GroupEntity group = this.entity(groupId);
        var user = this.users.require(userId);
        if (!group.organizationId.equals(user.organizationId())) {
            throw new GroupException(GroupException.Type.BAD_REQUEST, "A Group can contain only Users from its owning Organization");
        }
        group.memberIds.add(userId);
        this.groups.flush();
    }

    @Transactional
    public void removeMember(UUID groupId, UUID userId) {
        GroupEntity group = this.entity(groupId);
        group.memberIds.remove(userId);
        this.groups.flush();
    }

    @Transactional(readOnly = true)
    public GroupInfo require(UUID id) {
        GroupEntity group = this.entity(id);
        return new GroupInfo(group.id, group.organizationId, group.name);
    }

    @Transactional(readOnly = true)
    public List<UUID> groupsForUser(UUID userId) {
        return this.groups.findIdsContainingUser(userId);
    }

    public record GroupInfo(UUID id, UUID organizationId, String name) {}

    private GroupEntity entity(UUID id) {
        return this.groups.findById(id).orElseThrow(() -> new GroupException(GroupException.Type.NOT_FOUND, "Group not found"));
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) throw new GroupException(GroupException.Type.BAD_REQUEST, field + " is required");
        return value.trim();
    }
}
