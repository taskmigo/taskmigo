package io.taskmigo.identity.user;

import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.identity.group.GroupService;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates User creation with initial Access Control and Group assignments.
@Service
final class UserRegistrationApplicationService implements UserRegistrationService {

    private final UserService users;
    private final RoleService roles;
    private final GroupService groups;

    UserRegistrationApplicationService(UserService users, RoleService roles, GroupService groups) {
        this.users = users;
        this.roles = roles;
        this.groups = groups;
    }

    @Override
    @Transactional
    public UUID register(
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable Collection<UUID> roleIds,
        @Nullable Collection<UUID> groupIds
    ) {
        Set<UUID> requestedRoleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        Set<UUID> requestedGroupIds = groupIds == null ? Set.of() : Set.copyOf(groupIds);
        this.roles.requireRoles(requestedRoleIds);
        this.groups.requireGroups(requestedGroupIds);

        UUID id = this.users.create(username, emails, firstName, lastName, requestedRoleIds);
        requestedGroupIds.forEach(groupId -> this.groups.addMember(groupId, id));
        return id;
    }
}
