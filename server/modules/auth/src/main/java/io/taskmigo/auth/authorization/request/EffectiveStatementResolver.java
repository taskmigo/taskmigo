package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.statement.StatementEntity;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.StatementRepository;
import io.taskmigo.auth.group.GroupEntity;
import io.taskmigo.auth.group.GroupHierarchy;
import io.taskmigo.auth.group.GroupRepository;
import io.taskmigo.auth.role.RoleEntity;
import io.taskmigo.auth.role.RoleHierarchy;
import io.taskmigo.auth.role.RoleRepository;
import io.taskmigo.auth.user.UserEntity;
import io.taskmigo.auth.user.UserException;
import io.taskmigo.auth.user.UserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Resolves the complete deduplicated set of Statements effective for a User.
@Service
public class EffectiveStatementResolver {

    private final UserRepository users;
    private final GroupRepository groups;
    private final RoleRepository roles;
    private final StatementRepository statements;

    EffectiveStatementResolver(
        UserRepository users,
        GroupRepository groups,
        RoleRepository roles,
        StatementRepository statements
    ) {
        this.users = users;
        this.groups = groups;
        this.roles = roles;
        this.statements = statements;
    }

    /// Combines direct User Statements with Statements reachable through User Roles and Groups.
    ///
    /// The graph is loaded once per entity type and traversed with visited-node semantics, so shared descendants and
    /// corrupted cycles cannot cause duplicate results or infinite traversal. Results are ordered by Statement id.
    ///
    /// @param userId the User whose effective authorization Statements are required
    /// @return every effective Statement exactly once
    /// @throws UserException if the User does not exist
    @Transactional(readOnly = true)
    public List<StatementInfo> resolve(UUID userId) {
        UserEntity user = this.users
            .findById(userId)
            .orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"));
        List<GroupEntity> allGroups = this.groups.findAllByOrderByIdAsc();
        List<RoleEntity> allRoles = this.roles.findAllByOrderByIdAsc();

        Map<UUID, GroupEntity> groupsById = indexGroups(allGroups);
        Set<UUID> groupRoots = allGroups
            .stream()
            .filter(group -> group.memberIds().contains(user.id()))
            .map(GroupEntity::id)
            .collect(Collectors.toSet());
        Set<UUID> roleRoots = new HashSet<>(user.roleIds());
        GroupHierarchy groupHierarchy = GroupHierarchy.from(allGroups);
        for (UUID groupId : groupHierarchy.reachableFrom(groupRoots)) {
            GroupEntity group = groupsById.get(groupId);
            if (group != null) roleRoots.addAll(group.roleIds());
        }

        Map<UUID, RoleEntity> rolesById = indexRoles(allRoles);
        Set<UUID> statementIds = new HashSet<>(user.statementIds());
        Set<UUID> knownRoleRoots = new HashSet<>(roleRoots);
        knownRoleRoots.retainAll(rolesById.keySet());
        for (UUID roleId : RoleHierarchy.from(allRoles).reachableFrom(knownRoleRoots)) {
            RoleEntity role = rolesById.get(roleId);
            if (role != null) statementIds.addAll(role.statementIds());
        }

        return this.statements
            .findAllByIdIn(statementIds)
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .map(StatementEntity::info)
            .toList();
    }

    private static Map<UUID, GroupEntity> indexGroups(Collection<GroupEntity> entities) {
        Map<UUID, GroupEntity> indexed = new HashMap<>();
        for (GroupEntity entity : entities) indexed.put(entity.id(), entity);
        return indexed;
    }

    private static Map<UUID, RoleEntity> indexRoles(Collection<RoleEntity> entities) {
        Map<UUID, RoleEntity> indexed = new HashMap<>();
        for (RoleEntity entity : entities) indexed.put(entity.id(), entity);
        return indexed;
    }
}
