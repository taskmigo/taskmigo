package io.taskmigo.web.api.v0.feature.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.access.AccessService;
import io.taskmigo.web.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.web.api.v0.testing.TaskmigoApiClient.CreateRoleRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;

class RoleApiIntegrationTest extends ApiIntegrationTestSupport {

    private final AccessService access;
    private final JdbcTemplate jdbc;

    RoleApiIntegrationTest(AccessService access, JdbcTemplate jdbc) {
        this.access = access;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("creates a role with unique child roles")
    void shouldCreateRoleWithUniqueChildRolesWhenChildRolesAreProvided() {
        UUID child = this.access.createRole("Child", null, Set.of());
        UUID grandchild = this.access.createRole("Grandchild", null, Set.of());
        this.access.setChildRoles(child, Set.of(grandchild));

        UUID created = this.api()
            .roles()
            .create(new CreateRoleRequest("Parent", null, null, List.of(child, child)));

        assertThat(this.access.descendantRoles(created))
            .extracting(AccessService.RoleInfo::id)
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
            .create(new CreateRoleRequest("Leaf", null, null, null));

        assertThat(this.access.descendantRoles(created)).isEmpty();
    }

    @Test
    @DisplayName("rejects a role with an unknown child without persisting it")
    void shouldRollBackRoleCreationWhenChildRoleIsUnknown() {
        Integer before = this.jdbc.queryForObject("select count(*) from roles", Integer.class);

        assertThatThrownBy(() ->
            this.api()
                .roles()
                .create(new CreateRoleRequest("Invalid parent", null, null, List.of(UUID.randomUUID())))
        ).isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, exception ->
            assertThat(exception.getResponseBodyAsString()).contains("One or more child Roles do not exist")
        );

        assertThat(this.jdbc.queryForObject("select count(*) from roles", Integer.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("lists roles with offset pagination")
    void shouldListRolesWithOffsetPaginationWhenNoFiltersAreProvided() {
        this.access.createRole("Offset role one", null, Set.of());
        this.access.createRole("Offset role two", null, Set.of());

        String response = this.api().get("/api/v0/roles?page=2&pageSize=1");

        assertThat(response)
            .contains("\"code\":\"resource.role.listed\"")
            .contains("\"type\":\"offset\"")
            .contains("\"currentPage\":2")
            .contains("\"pageSize\":1")
            .contains("\"totalItems\":")
            .contains("\"totalPages\":");
    }
}
