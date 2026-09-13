package io.taskmigo.identity.authorization.request;

import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.spi.EffectiveStatementResolver;
import io.taskmigo.identity.persistence.group.GroupEntity;
import io.taskmigo.identity.persistence.group.GroupRepository;
import io.taskmigo.identity.persistence.role.RoleEntity;
import io.taskmigo.identity.persistence.role.RoleRepository;
import io.taskmigo.identity.persistence.statement.StatementEntity;
import io.taskmigo.identity.persistence.statement.StatementRepository;
import io.taskmigo.identity.persistence.user.UserEntity;
import io.taskmigo.identity.persistence.user.UserRepository;
import io.taskmigo.identity.user.UserException;
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
public class DatabaseEffectiveStatementResolver implements EffectiveStatementResolver {

    private final UserRepository users;
    private final GroupRepository groups;
    private final RoleRepository roles;
    private final StatementRepository statements;

    DatabaseEffectiveStatementResolver(
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
    /// descendants and corrupted cycles cannot cause duplicate results or infinite traversal. The independent
    /// repeatable-read transaction intentionally isolates the authorization snapshot from any caller transaction.
    /// Results are ordered by Statement id and include each row's database-owned `updated_at` revision.
    ///
    /// @param userId the User whose effective authorization Statements are required
    /// @return every effective Statement exactly once with its persisted revision
    /// @throws UserException if the User does not exist
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    @Override
    public List<EffectiveStatement> resolve(UUID userId) {
        return this.resolveEntities(userId)
            .stream()
            .map(entity -> new EffectiveStatement(entity.info(), entity.updatedAt()))
            .toList();
    }

    private List<StatementEntity> resolveEntities(UUID userId) {
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
            .toList();
    }
}
