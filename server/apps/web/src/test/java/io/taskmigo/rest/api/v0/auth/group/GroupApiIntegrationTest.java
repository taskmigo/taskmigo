package io.taskmigo.rest.api.v0.auth.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.group.GroupService;
import io.taskmigo.auth.role.RoleInfo;
import io.taskmigo.auth.role.RoleService;
import io.taskmigo.rest.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.CreateGroupRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;

class GroupApiIntegrationTest extends ApiIntegrationTestSupport {

    private final RoleService access;
    private final GroupService groups;
    private final JdbcTemplate jdbc;

    GroupApiIntegrationTest(RoleService access, GroupService groups, JdbcTemplate jdbc) {
        this.access = access;
        this.groups = groups;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("creates a group with unique child groups and roles")
    void shouldCreateGroupWithUniqueRelationshipsWhenChildrenAndRolesAreProvided() {
        UUID employee = this.access.createRole(uniqueRoleName("EmployeeRole"), null, Set.of());
        UUID developer = this.access.createRole(uniqueRoleName("DeveloperRole"), null, Set.of(employee));
        UUID backend = this.groups.create("Backend", null, Set.of(), Set.of(developer));

        UUID created = this.api()
            .groups()
            .create(
                new CreateGroupRequest("Engineering", null, List.of(backend, backend), List.of(employee, employee))
            );

        assertThat(this.groups.effectiveRoles(created))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(employee, developer).stream().sorted().toList());
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from group_hierarchy where parent_group_id = ? and child_group_id = ?",
                Integer.class,
                created,
                backend
            )
        ).isEqualTo(1);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from group_roles where group_id = ? and role_id = ?",
                Integer.class,
                created,
                employee
            )
        ).isEqualTo(1);
    }

    @Test
    @DisplayName("creates a group without relationships")
    void shouldCreateGroupWhenRelationshipsAreOmitted() {
        UUID created = this.api()
            .groups()
            .create(new CreateGroupRequest("Leaf", null, null, null));

        assertThat(this.groups.effectiveRoles(created)).isEmpty();
    }

    @Test
    @DisplayName("lists groups with their children using offset pagination")
    void shouldListGroupsWithChildrenWhenOffsetPaginationIsRequested() {
        UUID leaf = this.groups.create("Leaf", "A descendant", Set.of(), Set.of());
        UUID root = this.groups.create("Root", "A parent", Set.of(leaf), Set.of());

        String response = this.api().get("/api/v0/groups?page=1&pageSize=100");

        assertThat(response)
            .contains("\"id\":\"" + root + "\"")
            .contains("\"name\":\"Root\"")
            .contains("\"description\":\"A parent\"")
            .contains("\"children\":[{\"id\":\"" + leaf + "\"")
            .contains("\"type\":\"offset\"")
            .contains("\"currentPage\":1")
            .contains("\"pageSize\":100")
            .contains("\"totalItems\":");
    }

    @Test
    @DisplayName("rejects unknown group and role relationships without persisting the group")
    void shouldRollBackGroupCreationWhenRelationshipsAreUnknown() {
        Integer before = this.jdbc.queryForObject("select count(*) from groups", Integer.class);

        assertThatThrownBy(() ->
            this.api()
                .groups()
                .create(new CreateGroupRequest("Invalid", null, List.of(UUID.randomUUID()), null))
        ).isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, exception ->
            assertThat(exception.getResponseBodyAsString()).contains("One or more child Groups do not exist")
        );

        assertThatThrownBy(() ->
            this.api()
                .groups()
                .create(new CreateGroupRequest("Invalid", null, null, List.of(UUID.randomUUID())))
        ).isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, exception ->
            assertThat(exception.getResponseBodyAsString()).contains("One or more Roles do not exist")
        );

        assertThat(this.jdbc.queryForObject("select count(*) from groups", Integer.class)).isEqualTo(before);
    }

    private static String uniqueRoleName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
