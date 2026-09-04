package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.statement.StatementEntity;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.StatementRepository;
import io.taskmigo.auth.group.GroupEntity;
import io.taskmigo.auth.group.GroupRepository;
import io.taskmigo.auth.role.RoleEntity;
import io.taskmigo.auth.role.RoleRepository;
import io.taskmigo.auth.user.UserEntity;
import io.taskmigo.auth.user.UserException;
import io.taskmigo.auth.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
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
    /// Each hierarchy frontier is fetched in one targeted batch and traversed with visited-node semantics, so shared
    /// descendants and corrupted cycles cannot cause duplicate results or infinite traversal. Results are ordered by
    /// Statement id.
    ///
    /// @param userId the User whose effective authorization Statements are required
    /// @return every effective Statement exactly once
    /// @throws UserException if the User does not exist
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    public List<StatementInfo> resolve(UUID userId) {
        UserEntity user = this.users
            .findById(userId)
            .orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"));
        Set<UUID> statementIds = new HashSet<>(user.statementIds());
        Set<UUID> roleIds = new HashSet<>(user.roleIds());
        List<UUID> directGroupIds = this.groups
            .findDistinctByMemberIdsContains(user.id())
            .stream()
            .map(GroupEntity::id)
            .sorted()
            .toList();
        Set<UUID> groupIds = new HashSet<>(directGroupIds);
        if (!directGroupIds.isEmpty()) {
            groupIds.addAll(this.groups.findDescendantGroupIds(directGroupIds));
            for (GroupEntity group : this.groups.findDistinctByIdIn(groupIds)) {
                roleIds.addAll(group.roleIds());
            }
        }

        Set<UUID> reachableRoleIds = new HashSet<>(roleIds);
        if (!roleIds.isEmpty()) {
            reachableRoleIds.addAll(this.roles.findDescendantRoleIds(roleIds));
            for (RoleEntity role : this.roles.findDistinctByIdIn(reachableRoleIds)) {
                statementIds.addAll(role.statementIds());
            }
        }

        if (statementIds.isEmpty()) {
            return List.of();
        }
        return this.statements
            .findAllByIdIn(statementIds)
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .map(StatementEntity::info)
            .toList();
    }
}
