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
import java.util.ArrayList;
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
        List<GroupEntity> directGroups = this.groups.findDistinctByMemberIdsContains(user.id());
        List<UUID> groupFrontier = directGroups.stream().map(GroupEntity::id).sorted().toList();
        Set<UUID> visitedGroups = new HashSet<>();
        boolean firstGroupBatch = true;
        while (!groupFrontier.isEmpty()) {
            List<GroupEntity> currentGroups = firstGroupBatch
                ? directGroups
                : this.groups.findDistinctByParentGroups_IdIn(groupFrontier);
            firstGroupBatch = false;
            List<UUID> nextGroupFrontier = new ArrayList<>();
            for (GroupEntity group : currentGroups) {
                if (visitedGroups.add(group.id())) {
                    roleIds.addAll(group.roleIds());
                    nextGroupFrontier.add(group.id());
                }
            }
            groupFrontier = nextGroupFrontier.stream().sorted().toList();
        }

        List<UUID> roleFrontier = roleIds.stream().sorted().toList();
        Set<UUID> visitedRoles = new HashSet<>();
        boolean firstRoleBatch = true;
        while (!roleFrontier.isEmpty()) {
            List<RoleEntity> currentRoles = firstRoleBatch
                ? this.roles.findDistinctByIdIn(roleFrontier)
                : this.roles.findDistinctByParentRoles_IdIn(roleFrontier);
            firstRoleBatch = false;
            List<UUID> nextRoleFrontier = new ArrayList<>();
            for (RoleEntity role : currentRoles) {
                if (visitedRoles.add(role.id())) {
                    statementIds.addAll(role.statementIds());
                    nextRoleFrontier.add(role.id());
                }
            }
            roleFrontier = nextRoleFrontier.stream().sorted().toList();
        }

        if (statementIds.isEmpty()) return List.of();
        return this.statements
            .findAllByIdIn(statementIds)
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .map(StatementEntity::info)
            .toList();
    }
}
