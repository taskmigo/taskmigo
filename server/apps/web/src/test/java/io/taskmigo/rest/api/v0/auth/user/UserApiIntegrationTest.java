package io.taskmigo.rest.api.v0.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.request.EffectiveStatementResolver;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.group.GroupService;
import io.taskmigo.auth.role.RoleInfo;
import io.taskmigo.auth.role.RoleService;
import io.taskmigo.auth.user.UserAuthorizationResourceAdapter;
import io.taskmigo.auth.user.UserService;
import io.taskmigo.rest.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.CreateGroupRequest;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.CreateRoleRequest;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.CreateStatementRequest;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.CreateUserRequest;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.StatementApiTarget;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.StatementTarget;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;

class UserApiIntegrationTest extends ApiIntegrationTestSupport {

    private final RoleService access;
    private final GroupService groups;
    private final UserService users;
    private final UserAuthorizationResourceAdapter userResources;
    private final EffectiveStatementResolver statementResolver;
    private final JdbcTemplate jdbc;
    private final Statistics statistics;

    UserApiIntegrationTest(
        RoleService access,
        GroupService groups,
        UserService users,
        UserAuthorizationResourceAdapter userResources,
        EffectiveStatementResolver statementResolver,
        JdbcTemplate jdbc,
        EntityManagerFactory entityManagerFactory
    ) {
        this.access = access;
        this.groups = groups;
        this.users = users;
        this.userResources = userResources;
        this.statementResolver = statementResolver;
        this.jdbc = jdbc;
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    /**
     * Verifies that the user collection exposes the shared offset pagination contract.
     *
     * Given: the application contains bootstrap and test Users.
     * Expect: GET users returns an offset page with the requested page size and pagination metadata.
     */
    @Test
    @DisplayName("lists users with offset pagination")
    void shouldListUsersWithOffsetPaginationWhenPageParametersAreProvided() {
        // Arrange
        String response = this.api().get("/api/v0/users?page=1&pageSize=1");

        // Act
        // The public API client has executed the GET request; retain its raw response for contract assertions.

        // Assert
        assertThat(response)
            .contains("\"code\":\"resource.user.listed\"")
            .contains("\"type\":\"offset\"")
            .contains("\"currentPage\":1")
            .contains("\"pageSize\":1")
            .contains("\"totalItems\":")
            .contains("\"totalPages\":");
    }

    /**
     * Verifies that persisted user resources are resolved with one bounded database batch instead of one query per
     * user.
     *
     * Given: eight persisted users selected as request resources.
     * Expect: the adapter returns all users and Hibernate executes a fixed small number of statements independent of
     * user count.
     */
    @Test
    @DisplayName("resolves multiple user resources without N plus one queries")
    void shouldResolveUserResourcesWithoutNPlusOneQueries() {
        // Arrange
        List<UUID> userIds = new ArrayList<>();
        IntStream.range(0, 8).forEach(index ->
            userIds.add(
                this.users.create(
                    "resource-query-user-" + UUID.randomUUID(),
                    Set.of("resource-query-" + UUID.randomUUID() + "@example.com"),
                    "Resource",
                    "User"
                )
            )
        );
        this.statistics.clear();

        // Act
        Map<String, Map<String, ?>> resources = this.userResources.resolve(
            userIds.stream().map(UUID::toString).toList()
        );

        // Assert
        assertThat(resources).hasSize(8);
        assertThat(this.statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("creates users with optional role and group assignments")
    void shouldCreateUsersWhenOptionalAssignmentsAreProvided() {
        UUID employee = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("EmployeeRole"), null, Set.of()));
        UUID developer = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("DeveloperRole"), null, Set.of(employee)));
        UUID engineering = this.api()
            .groups()
            .create(new CreateGroupRequest("Engineering", null, Set.of(), Set.of(developer)));

        UUID noAssignments = this.create("none", Set.of(), Set.of());
        UUID withRoles = this.create("roles", List.of(developer, developer), Set.of());
        UUID withGroups = this.create("groups", Set.of(), List.of(engineering, engineering));
        UUID withBoth = this.create("both", Set.of(employee), Set.of(engineering));

        assertThat(this.groups.effectiveRolesForUser(noAssignments)).isEmpty();
        assertThat(this.groups.effectiveRolesForUser(withRoles))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(employee, developer).stream().sorted().toList());
        assertThat(this.groups.effectiveRolesForUser(withGroups))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(employee, developer).stream().sorted().toList());
        assertThat(this.groups.effectiveRolesForUser(withBoth))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(employee, developer).stream().sorted().toList());
        assertThat(
            this.jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class, withRoles)
        ).isEqualTo(1);
        assertThat(
            this.jdbc.queryForObject("select count(*) from group_members where user_id = ?", Integer.class, withGroups)
        ).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects unknown assignments without persisting a user")
    void shouldRollBackUserCreationWhenAssignmentsAreUnknown() {
        Integer before = this.jdbc.queryForObject("select count(*) from users", Integer.class);

        assertThatThrownBy(() ->
            this.create("invalid-role", List.of(UUID.randomUUID()), Set.of())
        ).isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, exception ->
            assertThat(exception.getResponseBodyAsString()).contains("One or more Roles do not exist")
        );
        assertThatThrownBy(() ->
            this.create("invalid-group", Set.of(), List.of(UUID.randomUUID()))
        ).isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, exception ->
            assertThat(exception.getResponseBodyAsString()).contains("One or more Groups do not exist")
        );

        assertThat(this.jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("resolves hierarchy roles without inheriting ancestors")
    void shouldResolveEffectiveRolesWhenHierarchyContainsDirectAndGroupAssignments() {
        UUID roleB = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("Role_B"), null, Set.of()));
        UUID roleA = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("Role_A"), null, List.of(roleB)));
        UUID developer = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("DeveloperRole"), null, Set.of()));
        UUID backendDeveloper = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("BackendDeveloper"), null, List.of(developer)));
        UUID employee = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("EmployeeRole"), null, Set.of()));
        UUID backend = this.api()
            .groups()
            .create(new CreateGroupRequest("Backend", null, Set.of(), List.of(backendDeveloper)));
        UUID engineering = this.api()
            .groups()
            .create(new CreateGroupRequest("Engineering", null, List.of(backend), List.of(employee)));

        UUID user = this.create("hierarchy-user", List.of(roleA, roleA), List.of(engineering, engineering));
        UUID childRoleUser = this.create("child-role-user", List.of(roleB), Set.of());
        UUID backendUser = this.create("backend-user", Set.of(), List.of(backend));

        assertThat(this.groups.effectiveRolesForUser(user))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(
                List.of(roleA, roleB, employee, backendDeveloper, developer).stream().sorted().toList()
            );
        assertThat(this.groups.effectiveRolesForUser(childRoleUser)).extracting(RoleInfo::id).containsExactly(roleB);
        assertThat(this.groups.effectiveRolesForUser(backendUser))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(backendDeveloper, developer).stream().sorted().toList());

        assertThatThrownBy(() -> this.access.setChildRoles(roleB, Set.of(roleA))).hasMessageContaining(
            "Role hierarchy must be acyclic"
        );
        assertThatThrownBy(() -> this.groups.setChildGroups(backend, Set.of(engineering))).hasMessageContaining(
            "Group hierarchy must be acyclic"
        );
        assertThat(this.groups.effectiveRolesForUser(user))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(
                List.of(roleA, roleB, employee, backendDeveloper, developer).stream().sorted().toList()
            );
    }

    /**
     * Verifies that effective Statements include every direct and inherited source exactly once.
     *
     * Given: a User with a direct Statement and Role, a nested Group with a Role, and overlapping assignments.
     * Expect: one resolver call returns the direct, Role-inherited, Group-derived, and deduplicated Statements.
     */
    @Test
    @DisplayName("resolves direct and inherited statements without duplicates")
    void shouldResolveAllEffectiveStatementsWhenUserHasMixedAssignments() {
        // Arrange
        String inheritedRoleName = "inherited-role-" + UUID.randomUUID();
        String groupStatementName = "group-" + UUID.randomUUID();
        String directStatementName = "direct-" + UUID.randomUUID();
        String sharedStatementName = "shared-" + UUID.randomUUID();
        UUID inheritedRoleStatement = this.createStatement(inheritedRoleName);
        UUID groupStatement = this.createStatement(groupStatementName);
        UUID directStatement = this.createStatement(directStatementName);
        UUID sharedStatement = this.createStatement(sharedStatementName);

        UUID childRole = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("ChildRole"), null, Set.of()));
        UUID parentRole = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("ParentRole"), null, Set.of(childRole)));
        UUID groupRole = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("GroupRole"), null, Set.of()));
        this.api().roles().replaceStatements(childRole, List.of(inheritedRoleStatement, sharedStatement));
        this.api().roles().replaceStatements(parentRole, List.of(sharedStatement));
        this.api().roles().replaceStatements(groupRole, List.of(groupStatement));

        UUID nestedGroup = this.api()
            .groups()
            .create(new CreateGroupRequest("Nested", null, Set.of(), Set.of()));
        UUID parentGroup = this.api()
            .groups()
            .create(new CreateGroupRequest("Parent group", null, List.of(nestedGroup), Set.of()));
        this.groups.setRoles(nestedGroup, Set.of(childRole, groupRole));
        UUID user = this.create("mixed-statements", List.of(parentRole), List.of(parentGroup));
        this.users.setStatements(user, List.of(directStatement, sharedStatement));

        // Act
        List<String> names = this.statementResolver.resolve(user).stream().map(StatementInfo::name).toList();

        // Assert
        assertThat(names).containsExactlyInAnyOrder(
            inheritedRoleName,
            groupStatementName,
            directStatementName,
            sharedStatementName
        );
        assertThat(names).doesNotHaveDuplicates();
    }

    private UUID create(String username, Collection<UUID> roleIds, Collection<UUID> groupIds) {
        return this.api()
            .users()
            .create(
                new CreateUserRequest(username, Set.of(username + "@example.com"), "Test", "User", roleIds, groupIds)
            );
    }

    /**
     * Verifies that a User's direct Statement set can be replaced and duplicate ids are collapsed.
     *
     * Given: a persisted User and two Statements, with a replacement request containing one duplicate id.
     * Expect: the User-to-Statement join table contains exactly the two requested relationships.
     */
    @Test
    @DisplayName("replaces a user's direct statements")
    void shouldReplaceUserStatementsWhenAssignmentsAreProvided() {
        // Arrange
        UUID user = this.create("statement-user", Set.of(), Set.of());
        UUID first = this.createStatement("user-first-" + UUID.randomUUID());
        UUID second = this.createStatement("user-second-" + UUID.randomUUID());

        // Act
        this.api()
            .users()
            .replaceStatements(user, List.of(first, second, first));

        // Assert
        assertThat(
            this.jdbc.queryForList(
                "select statement_id from user_statements where user_id = ? order by statement_id",
                UUID.class,
                user
            )
        ).containsExactlyInAnyOrder(first, second);
    }

    /**
     * Verifies that an unknown User id is rejected before any Statement relationship can be created.
     *
     * Given: an unknown User id and a valid Statement id.
     * Expect: a bad-request response and no row in the User-to-Statement join table.
     */
    @Test
    @DisplayName("rejects statements for an unknown user")
    void shouldRejectUserStatementsWhenUserIsUnknown() {
        // Arrange
        UUID statement = this.createStatement("unknown-user-" + UUID.randomUUID());
        UUID unknownUser = UUID.randomUUID();

        // Act
        assertThatThrownBy(() ->
            this.api().users().replaceStatements(unknownUser, List.of(statement))
        ).isInstanceOfSatisfying(HttpClientErrorException.NotFound.class, exception ->
            assertThat(exception.getResponseBodyAsString()).contains("User not found")
        );

        // Assert
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from user_statements where user_id = ?",
                Integer.class,
                unknownUser
            )
        ).isZero();
    }

    private UUID createStatement(String name) {
        return this.api()
            .statements()
            .create(
                new CreateStatementRequest(
                    name,
                    null,
                    "allow",
                    "request",
                    new StatementTarget(new StatementApiTarget("GET", "/api/v0/users")),
                    "export default () => true;"
                )
            );
    }

    private static String uniqueRoleName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
