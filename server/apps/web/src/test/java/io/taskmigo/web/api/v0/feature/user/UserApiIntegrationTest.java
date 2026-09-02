package io.taskmigo.web.api.v0.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.access.AccessService;
import io.taskmigo.group.GroupService;
import io.taskmigo.user.UserService;
import io.taskmigo.web.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.web.api.v0.testing.TaskmigoApiClient.CreateGroupRequest;
import io.taskmigo.web.api.v0.testing.TaskmigoApiClient.CreateRoleRequest;
import io.taskmigo.web.api.v0.testing.TaskmigoApiClient.CreateUserRequest;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;

class UserApiIntegrationTest extends ApiIntegrationTestSupport {

    private final AccessService access;
    private final GroupService groups;
    private final UserService users;
    private final JdbcTemplate jdbc;

    UserApiIntegrationTest(AccessService access, GroupService groups, UserService users, JdbcTemplate jdbc) {
        this.access = access;
        this.groups = groups;
        this.users = users;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("creates users with optional role and group assignments")
    void shouldCreateUsersWhenOptionalAssignmentsAreProvided() {
        UUID employee = this.api()
            .roles()
            .create(new CreateRoleRequest("Employee", null, Set.of(), Set.of()));
        UUID developer = this.api()
            .roles()
            .create(new CreateRoleRequest("Developer", null, Set.of(), Set.of(employee)));
        UUID engineering = this.api()
            .groups()
            .create(new CreateGroupRequest("Engineering", null, Set.of(), Set.of(developer)));

        UUID noAssignments = this.create("none", Set.of(), Set.of());
        UUID withRoles = this.create("roles", List.of(developer, developer), Set.of());
        UUID withGroups = this.create("groups", Set.of(), List.of(engineering, engineering));
        UUID withBoth = this.create("both", Set.of(employee), Set.of(engineering));

        assertThat(this.groups.effectiveRolesForUser(noAssignments)).isEmpty();
        assertThat(this.groups.effectiveRolesForUser(withRoles))
            .extracting(AccessService.RoleInfo::id)
            .containsExactlyElementsOf(List.of(employee, developer).stream().sorted().toList());
        assertThat(this.groups.effectiveRolesForUser(withGroups))
            .extracting(AccessService.RoleInfo::id)
            .containsExactlyElementsOf(List.of(employee, developer).stream().sorted().toList());
        assertThat(this.groups.effectiveRolesForUser(withBoth))
            .extracting(AccessService.RoleInfo::id)
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
            .create(new CreateRoleRequest("Role B", null, Set.of(), Set.of()));
        UUID roleA = this.api()
            .roles()
            .create(new CreateRoleRequest("Role A", null, Set.of(), List.of(roleB)));
        UUID developer = this.api()
            .roles()
            .create(new CreateRoleRequest("Developer", null, Set.of(), Set.of()));
        UUID backendDeveloper = this.api()
            .roles()
            .create(new CreateRoleRequest("Backend Developer", null, Set.of(), List.of(developer)));
        UUID employee = this.api()
            .roles()
            .create(new CreateRoleRequest("Employee", null, Set.of(), Set.of()));
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
            .extracting(AccessService.RoleInfo::id)
            .containsExactlyElementsOf(
                List.of(roleA, roleB, employee, backendDeveloper, developer).stream().sorted().toList()
            );
        assertThat(this.groups.effectiveRolesForUser(childRoleUser))
            .extracting(AccessService.RoleInfo::id)
            .containsExactly(roleB);
        assertThat(this.groups.effectiveRolesForUser(backendUser))
            .extracting(AccessService.RoleInfo::id)
            .containsExactlyElementsOf(List.of(backendDeveloper, developer).stream().sorted().toList());

        assertThatThrownBy(() -> this.access.setChildRoles(roleB, Set.of(roleA))).hasMessageContaining(
            "Role hierarchy must be acyclic"
        );
        assertThatThrownBy(() -> this.groups.setChildGroups(backend, Set.of(engineering))).hasMessageContaining(
            "Group hierarchy must be acyclic"
        );
        assertThat(this.groups.effectiveRolesForUser(user))
            .extracting(AccessService.RoleInfo::id)
            .containsExactlyElementsOf(
                List.of(roleA, roleB, employee, backendDeveloper, developer).stream().sorted().toList()
            );
    }

    private UUID create(String username, Collection<UUID> roleIds, Collection<UUID> groupIds) {
        return this.api()
            .users()
            .create(
                new CreateUserRequest(username, Set.of(username + "@example.com"), "Test", "User", roleIds, groupIds)
            );
    }
}
