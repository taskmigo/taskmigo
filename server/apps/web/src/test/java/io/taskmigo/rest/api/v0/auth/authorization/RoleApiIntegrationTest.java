package io.taskmigo.rest.api.v0.auth.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.role.RoleInfo;
import io.taskmigo.auth.role.RoleService;
import io.taskmigo.rest.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.CreateRoleRequest;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.CreateStatementRequest;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.StatementApiTarget;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.StatementTarget;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;

class RoleApiIntegrationTest extends ApiIntegrationTestSupport {

    private final RoleService access;
    private final JdbcTemplate jdbc;

    RoleApiIntegrationTest(RoleService access, JdbcTemplate jdbc) {
        this.access = access;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("creates a role with unique child roles")
    void shouldCreateRoleWithUniqueChildRolesWhenChildRolesAreProvided() {
        UUID child = this.access.createRole(uniqueRoleName("ChildRole"), null, Set.of());
        UUID grandchild = this.access.createRole(uniqueRoleName("Grandchild"), null, Set.of());
        this.access.setChildRoles(child, Set.of(grandchild));

        UUID created = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("ParentRole"), null, List.of(child, child)));

        assertThat(this.access.descendantRoles(created))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(child, grandchild).stream().sorted().toList());
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from role_hierarchy where parent_role_id = ? and child_role_id = ?",
                Integer.class,
                created,
                child
            )
        ).isEqualTo(1);
    }

    @Test
    @DisplayName("creates a role without child roles")
    void shouldCreateRoleWhenChildRolesAreOmitted() {
        UUID created = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("LeafRole"), null, null));

        assertThat(this.access.descendantRoles(created)).isEmpty();
    }

    @Test
    @DisplayName("rejects a role with an unknown child without persisting it")
    void shouldRollBackRoleCreationWhenChildRoleIsUnknown() {
        Integer before = this.jdbc.queryForObject("select count(*) from roles", Integer.class);

        assertThatThrownBy(() ->
            this.api()
                .roles()
                .create(new CreateRoleRequest(uniqueRoleName("InvalidParent"), null, List.of(UUID.randomUUID())))
        ).isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, exception ->
            assertThat(exception.getResponseBodyAsString()).contains("One or more child Roles do not exist")
        );

        assertThat(this.jdbc.queryForObject("select count(*) from roles", Integer.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("lists roles with offset pagination")
    void shouldListRolesWithOffsetPaginationWhenNoFiltersAreProvided() {
        this.access.createRole(uniqueRoleName("OffsetRoleOne"), null, Set.of());
        this.access.createRole(uniqueRoleName("OffsetRoleTwo"), null, Set.of());

        String response = this.api().get("/api/v0/roles?page=2&pageSize=1");

        assertThat(response)
            .contains("\"code\":\"resource.role.listed\"")
            .contains("\"type\":\"offset\"")
            .contains("\"currentPage\":2")
            .contains("\"pageSize\":1")
            .contains("\"totalItems\":")
            .contains("\"totalPages\":");
    }

    /**
     * Verifies that a Role's direct Statement set is replaced as a complete desired set.
     *
     * Given: a Role and two Statements, followed by assignments containing a duplicate and then one replacement.
     * Expect: the join table contains each requested Statement once and the removed Statement is no longer assigned.
     */
    @Test
    @DisplayName("replaces a role's direct statements")
    void shouldReplaceRoleStatementsWhenAssignmentsAreProvided() {
        // Arrange
        UUID role = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("StatementRole"), null, Set.of()));
        UUID first = this.createStatement("role-first-" + UUID.randomUUID());
        UUID second = this.createStatement("role-second-" + UUID.randomUUID());

        // Act
        this.api()
            .roles()
            .replaceStatements(role, List.of(first, first, second));
        this.api().roles().replaceStatements(role, List.of(second));

        // Assert
        assertThat(
            this.jdbc.queryForList(
                "select statement_id from role_statements where role_id = ? order by statement_id",
                UUID.class,
                role
            )
        ).containsExactly(second);
    }

    /**
     * Verifies that invalid Statement references do not partially replace a Role's existing assignments.
     *
     * Given: a Role assigned to one valid Statement and a replacement request containing an unknown Statement id.
     * Expect: the request is rejected and the original one-row relationship remains unchanged.
     */
    @Test
    @DisplayName("rejects unknown role statements without changing assignments")
    void shouldPreserveRoleStatementsWhenStatementIsUnknown() {
        // Arrange
        UUID role = this.api()
            .roles()
            .create(new CreateRoleRequest(uniqueRoleName("InvalidStatementRole"), null, Set.of()));
        UUID statement = this.createStatement("role-existing-" + UUID.randomUUID());
        this.api().roles().replaceStatements(role, List.of(statement));

        // Act
        assertThatThrownBy(() ->
            this.api().roles().replaceStatements(role, List.of(UUID.randomUUID()))
        ).isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, exception ->
            assertThat(exception.getResponseBodyAsString()).contains("One or more Statements do not exist")
        );

        // Assert
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from role_statements where role_id = ? and statement_id = ?",
                Integer.class,
                role,
                statement
            )
        ).isEqualTo(1);
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
                    new StatementTarget(new StatementApiTarget("GET", "/api/v0/roles")),
                    "export default () => true;"
                )
            );
    }

    private static String uniqueRoleName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
