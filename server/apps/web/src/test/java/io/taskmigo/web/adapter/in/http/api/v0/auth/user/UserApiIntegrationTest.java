package io.taskmigo.web.adapter.in.http.api.v0.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.request.application.port.out.EffectiveStatement;
import io.taskmigo.authorization.request.application.port.out.EffectiveStatementResolver;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.application.port.in.api.RoleService;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectRoleQueryService;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateGroupRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateRoleRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateStatementRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateUserRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.StatementApiTarget;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.StatementTarget;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;

class UserApiIntegrationTest extends ApiIntegrationTestSupport {

    private final RoleService access;
    private final GroupService groups;
    private final UserService users;
    private final EffectiveStatementResolver statementResolver;
    private final SubjectRoleQueryService effectiveRoles;
    private final JdbcTemplate jdbc;

    UserApiIntegrationTest(
        RoleService access,
        GroupService groups,
        UserService users,
        EffectiveStatementResolver statementResolver,
        SubjectRoleQueryService effectiveRoles,
        JdbcTemplate jdbc
    ) {
        this.access = access;
        this.groups = groups;
        this.users = users;
        this.statementResolver = statementResolver;
        this.effectiveRoles = effectiveRoles;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("lists users with offset pagination")
    void shouldListUsersWithOffsetPaginationWhenPageParametersAreProvided() {
        String response = this.api().get("/api/v0/users?page=1&pageSize=1");

        assertThat(response)
            .contains("\"code\":\"resource.user.listed\"")
            .contains("\"type\":\"offset\"")
            .contains("\"currentPage\":1")
            .contains("\"pageSize\":1")
            .contains("\"totalItems\":")
            .contains("\"totalPages\":");
    }

    /**
     * Verifies that runtime User creation normalizes accepted profile values through the public API.
     *
     * Given: username and name values padded with whitespace plus one email supplied with different casing.
     * Expect: the created User contains trimmed profile values, one lowercase email, and the normalized display name.
     */
    @Test
    @DisplayName("normalizes user profile and email values")
    void shouldNormalizeUserProfileWhenCreatingThroughApi() {
        // Arrange
        String username = "normalized-" + UUID.randomUUID().toString().replace("-", "");
        CreateUserRequest request = new CreateUserRequest(
            "  " + username + "  ",
            Set.of(username.toUpperCase() + "@EXAMPLE.COM", username + "@example.com"),
            "  Test  ",
            "  User  ",
            Set.of(),
            Set.of()
        );

        // Act
        UUID id = this.api().users().create(request);
        var created = this.users.require(id);

        // Assert
        assertThat(created.username()).isEqualTo(username);
        assertThat(created.firstName()).isEqualTo("Test");
        assertThat(created.lastName()).isEqualTo("User");
        assertThat(created.emails()).containsExactly(username + "@example.com");
        assertThat(created.displayName()).isEqualTo("Test User");
    }

    /**
     * Verifies that the reserved system username cannot be used by ordinary runtime User creation.
     *
     * Given: a public create-User request whose username is exactly `system`.
     * Expect: the API returns HTTP 400 and does not persist another User.
     */
    @Test
    @DisplayName("rejects the reserved system username")
    void shouldRejectReservedSystemUsernameWhenCreatingThroughApi() {
        // Arrange
        Integer before = this.jdbc.queryForObject("select count(*) from users", Integer.class);
        CreateUserRequest request = new CreateUserRequest(
            "system",
            Set.of("reserved-system@example.com"),
            "Reserved",
            "User",
            Set.of(),
            Set.of()
        );

        // Act + Assert
        assertThatThrownBy(() -> this.api().users().create(request)).isInstanceOf(
            HttpClientErrorException.BadRequest.class
        );
        assertThat(this.jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(before);
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
            .create(new CreateGroupRequest(uniqueGroupName("Engineering"), null, Set.of(), Set.of(developer)));

        UUID noAssignments = this.create("none", Set.of(), Set.of());
        UUID withRoles = this.create("roles", List.of(developer, developer), Set.of());
        UUID withGroups = this.create("groups", Set.of(), List.of(engineering, engineering));
        UUID withBoth = this.create("both", Set.of(employee), Set.of(engineering));

        assertThat(this.effectiveRoles.effectiveRolesForPrincipal(noAssignments)).isEmpty();
        assertThat(this.effectiveRoles.effectiveRolesForPrincipal(withRoles))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(employee, developer).stream().sorted().toList());
        assertThat(this.effectiveRoles.effectiveRolesForPrincipal(withGroups))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(employee, developer).stream().sorted().toList());
        assertThat(this.effectiveRoles.effectiveRolesForPrincipal(withBoth))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(employee, developer).stream().sorted().toList());
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from subject_role_bindings where subject_type = ? and subject_id = ?",
                Integer.class,
                "identity:user",
                withRoles
            )
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
            .create(new CreateGroupRequest(uniqueGroupName("Backend"), null, Set.of(), List.of(backendDeveloper)));
        UUID engineering = this.api()
            .groups()
            .create(new CreateGroupRequest(uniqueGroupName("Engineering"), null, List.of(backend), List.of(employee)));

        UUID user = this.create("hierarchy-user", List.of(roleA, roleA), List.of(engineering, engineering));
        UUID childRoleUser = this.create("child-role-user", List.of(roleB), Set.of());
        UUID backendUser = this.create("backend-user", Set.of(), List.of(backend));

        assertThat(this.effectiveRoles.effectiveRolesForPrincipal(user))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(
                List.of(roleA, roleB, employee, backendDeveloper, developer).stream().sorted().toList()
            );
        assertThat(this.effectiveRoles.effectiveRolesForPrincipal(childRoleUser))
            .extracting(RoleInfo::id)
            .containsExactly(roleB);
        assertThat(this.effectiveRoles.effectiveRolesForPrincipal(backendUser))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(backendDeveloper, developer).stream().sorted().toList());

        assertThatThrownBy(() -> this.access.setChildRoles(roleB, Set.of(roleA))).hasMessageContaining(
            "Role hierarchy must be acyclic"
        );
        assertThatThrownBy(() -> this.groups.setChildGroups(backend, Set.of(engineering))).hasMessageContaining(
            "Group hierarchy must be acyclic"
        );
        assertThat(this.effectiveRoles.effectiveRolesForPrincipal(user))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(
                List.of(roleA, roleB, employee, backendDeveloper, developer).stream().sorted().toList()
            );
    }

    @Test
    @DisplayName("resolves direct and inherited statements without duplicates")
    void shouldResolveAllEffectiveStatementsWhenUserHasMixedAssignments() {
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
            .create(new CreateGroupRequest(uniqueGroupName("Nested"), null, Set.of(), Set.of()));
        UUID parentGroup = this.api()
            .groups()
            .create(new CreateGroupRequest(uniqueGroupName("Parent group"), null, List.of(nestedGroup), Set.of()));
        this.groups.setRoles(nestedGroup, Set.of(childRole, groupRole));
        UUID user = this.create("mixed-statements", List.of(parentRole), List.of(parentGroup));
        this.users.setStatements(user, List.of(directStatement, sharedStatement));

        List<String> names = this.statementResolver
            .resolve(user)
            .stream()
            .map(EffectiveStatement::statement)
            .map(StatementInfo::code)
            .toList();

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

    @Test
    @DisplayName("replaces a user's direct statements")
    void shouldReplaceUserStatementsWhenAssignmentsAreProvided() {
        UUID user = this.create("statement-user", Set.of(), Set.of());
        UUID first = this.createStatement("user-first-" + UUID.randomUUID());
        UUID second = this.createStatement("user-second-" + UUID.randomUUID());

        this.api()
            .users()
            .replaceStatements(user, List.of(first, second, first));

        assertThat(
            this.jdbc.queryForList(
                "select statement_id from subject_statement_bindings where subject_type = ? and subject_id = ? order by statement_id",
                UUID.class,
                "identity:user",
                user
            )
        ).containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("rejects statements for an unknown user")
    void shouldRejectUserStatementsWhenUserIsUnknown() {
        UUID statement = this.createStatement("unknown-user-" + UUID.randomUUID());
        UUID unknownUser = UUID.randomUUID();

        assertThatThrownBy(() ->
            this.api().users().replaceStatements(unknownUser, List.of(statement))
        ).isInstanceOfSatisfying(HttpClientErrorException.NotFound.class, exception ->
            assertThat(exception.getResponseBodyAsString()).contains("User not found")
        );

        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from subject_statement_bindings where subject_type = ? and subject_id = ?",
                Integer.class,
                "identity:user",
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
                    "return true;"
                )
            );
    }

    private static String uniqueRoleName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private static String uniqueGroupName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
