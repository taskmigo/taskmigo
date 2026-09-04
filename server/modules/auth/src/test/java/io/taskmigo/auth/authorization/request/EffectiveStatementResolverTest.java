package io.taskmigo.auth.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.auth.authorization.statement.ApiInfo;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementEntity;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.StatementRepository;
import io.taskmigo.auth.authorization.statement.TargetInfo;
import io.taskmigo.auth.group.GroupEntity;
import io.taskmigo.auth.group.GroupRepository;
import io.taskmigo.auth.role.RoleEntity;
import io.taskmigo.auth.role.RoleRepository;
import io.taskmigo.auth.user.UserEntity;
import io.taskmigo.auth.user.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EffectiveStatementResolverTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CHILD_GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID DIRECT_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID GROUP_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID CHILD_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID DIRECT_STATEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID SHARED_STATEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID INHERITED_STATEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID GROUP_STATEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    private final UserRepository users = mock(UserRepository.class);
    private final GroupRepository groups = mock(GroupRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final StatementRepository statements = mock(StatementRepository.class);
    private final EffectiveStatementResolver resolver = new EffectiveStatementResolver(
        this.users,
        this.groups,
        this.roles,
        this.statements
    );

    /**
     * Verifies that effective Statements are resolved from direct assignments and reachable hierarchy nodes once.
     *
     * Given: a User with direct, Group-derived, and inherited Role assignments that share one Statement.
     * Expect: the resolver returns all four unique Statements and never loads unrelated full authorization graphs.
     */
    @Test
    @DisplayName("resolves direct and inherited statements through batched hierarchy queries")
    void shouldResolveUniqueStatementsWhenAssignmentsOverlapAcrossHierarchyLevels() {
        // Arrange
        UserEntity user = mock(UserEntity.class);
        GroupEntity group = group(GROUP_ID, Set.of(GROUP_ROLE_ID, DIRECT_ROLE_ID));
        GroupEntity childGroup = group(CHILD_GROUP_ID, Set.of(GROUP_ROLE_ID));
        RoleEntity directRole = role(DIRECT_ROLE_ID, Set.of(SHARED_STATEMENT_ID));
        RoleEntity groupRole = role(GROUP_ROLE_ID, Set.of(SHARED_STATEMENT_ID, GROUP_STATEMENT_ID));
        RoleEntity childRole = role(CHILD_ROLE_ID, Set.of(INHERITED_STATEMENT_ID));
        when(this.users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.id()).thenReturn(USER_ID);
        when(user.roleIds()).thenReturn(Set.of(DIRECT_ROLE_ID));
        when(user.statementIds()).thenReturn(Set.of(DIRECT_STATEMENT_ID));
        when(this.groups.findDistinctByMemberIdsContains(USER_ID)).thenReturn(List.of(group));
        when(this.groups.findDistinctByParentGroups_IdIn(List.of(GROUP_ID))).thenReturn(List.of(childGroup));
        when(this.groups.findDistinctByParentGroups_IdIn(List.of(CHILD_GROUP_ID))).thenReturn(List.of());
        when(this.roles.findDistinctByIdIn(List.of(DIRECT_ROLE_ID, GROUP_ROLE_ID))).thenReturn(
            List.of(directRole, groupRole)
        );
        when(this.roles.findDistinctByParentRoles_IdIn(List.of(DIRECT_ROLE_ID, GROUP_ROLE_ID))).thenReturn(
            List.of(childRole)
        );
        when(this.roles.findDistinctByParentRoles_IdIn(List.of(CHILD_ROLE_ID))).thenReturn(List.of());
        StatementEntity directStatement = statement(DIRECT_STATEMENT_ID, "direct");
        StatementEntity sharedStatement = statement(SHARED_STATEMENT_ID, "shared");
        StatementEntity inheritedStatement = statement(INHERITED_STATEMENT_ID, "inherited");
        StatementEntity groupStatement = statement(GROUP_STATEMENT_ID, "group");
        when(this.statements.findAllByIdIn(anyCollection())).thenReturn(
            List.of(directStatement, sharedStatement, inheritedStatement, groupStatement)
        );

        // Act
        List<String> names = this.resolver.resolve(USER_ID).stream().map(StatementInfo::name).toList();

        // Assert
        assertThat(names).containsExactly("direct", "shared", "inherited", "group");
        verify(this.groups, never()).findAll();
        verify(this.roles, never()).findAll();
    }

    /**
     * Verifies that a large effective Statement set is fetched through one deduplicated Statement query.
     *
     * Given: a User assigned to one Role containing approximately 500 distinct Statements.
     * Expect: all 500 Statements are returned while the resolver uses only targeted group and role repository methods.
     */
    @Test
    @DisplayName("resolves approximately 500 statements without loading unrelated graph data")
    void shouldResolveFiveHundredStatementsWhenOneRoleContainsLargeStatementSet() {
        // Arrange
        UserEntity user = mock(UserEntity.class);
        RoleEntity role = role(
            DIRECT_ROLE_ID,
            IntStream.range(0, 500).mapToObj(EffectiveStatementResolverTest::id).collect(Collectors.toSet())
        );
        when(this.users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.id()).thenReturn(USER_ID);
        when(user.roleIds()).thenReturn(Set.of(DIRECT_ROLE_ID));
        when(user.statementIds()).thenReturn(Set.of());
        when(this.groups.findDistinctByMemberIdsContains(USER_ID)).thenReturn(List.of());
        when(this.roles.findDistinctByIdIn(List.of(DIRECT_ROLE_ID))).thenReturn(List.of(role));
        when(this.roles.findDistinctByParentRoles_IdIn(List.of(DIRECT_ROLE_ID))).thenReturn(List.of());
        when(this.statements.findAllByIdIn(anyCollection())).thenReturn(
            IntStream.range(0, 500)
                .mapToObj(index -> statement(id(index), "statement-" + index))
                .toList()
        );

        // Act
        List<StatementInfo> result = this.resolver.resolve(USER_ID);

        // Assert
        assertThat(result).hasSize(500);
        verify(this.groups, never()).findAll();
        verify(this.roles, never()).findAll();
    }

    private static GroupEntity group(UUID id, Collection<UUID> roleIds) {
        GroupEntity group = mock(GroupEntity.class);
        when(group.id()).thenReturn(id);
        when(group.roleIds()).thenReturn(Set.copyOf(roleIds));
        return group;
    }

    private static RoleEntity role(UUID id, Collection<UUID> statementIds) {
        RoleEntity role = mock(RoleEntity.class);
        when(role.id()).thenReturn(id);
        when(role.statementIds()).thenReturn(Set.copyOf(statementIds));
        return role;
    }

    private static StatementEntity statement(UUID id, String name) {
        StatementEntity statement = mock(StatementEntity.class);
        when(statement.id()).thenReturn(id);
        when(statement.info()).thenReturn(
            new StatementInfo(
                id,
                name,
                null,
                Effect.ALLOW,
                Scope.REQUEST,
                new TargetInfo(new ApiInfo("GET", "/")),
                null
            )
        );
        return statement;
    }

    private static UUID id(int index) {
        return new UUID(1L, index);
    }
}
