package io.taskmigo.web.adapter.in.http.api.v0.auth.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.application.port.in.api.RoleService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectRoleQueryService;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateGroupRequest;
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
    private final SubjectRoleQueryService effectiveRoles;
    private final JdbcTemplate jdbc;

    GroupApiIntegrationTest(
        RoleService access,
        GroupService groups,
        SubjectRoleQueryService effectiveRoles,
        JdbcTemplate jdbc
    ) {
        this.access = access;
        this.groups = groups;
        this.effectiveRoles = effectiveRoles;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("creates a group with unique child groups and roles")
    void shouldCreateGroupWithUniqueRelationshipsWhenChildrenAndRolesAreProvided() {
        String employeeCode = uniqueRoleName("EmployeeRole");
        UUID employee = this.access.createRole(employeeCode, employeeCode, null, Set.of());
        String developerCode = uniqueRoleName("DeveloperRole");
        UUID developer = this.access.createRole(developerCode, developerCode, null, Set.of(employee));
        UUID backend = this.groups.create("Backend", "Backend", null, Set.of(), Set.of(developer));

        UUID created = this.api()
            .groups()
            .create(
                new CreateGroupRequest("Engineering", null, List.of(backend, backend), List.of(employee, employee))
            );

        assertThat(this.effectiveRoles.effectiveRoles(IdentitySubjects.group(created)))
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
                "select count(*) from subject_role_bindings where subject_type = ? and subject_id = ? and role_id = ?",
                Integer.class,
                "identity:group",
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
            .create(new CreateGroupRequest(uniqueGroupCode("Leaf"), null, null, null));

        assertThat(this.effectiveRoles.effectiveRoles(IdentitySubjects.group(created))).isEmpty();
    }

    /**
     * Verifies that duplicate runtime Group codes are reported as a stable conflict instead of a persistence failure.
     *
     * Given: a Group has already been created through the public API with a generated code.
     * Expect: creating another Group with the same code returns HTTP 409 and a domain conflict message.
     */
    @Test
    @DisplayName("rejects duplicate group codes with a conflict")
    void shouldReturnConflictWhenGroupCodeAlreadyExists() {
        // Arrange
        String code = uniqueGroupCode("Duplicate");
        CreateGroupRequest request = new CreateGroupRequest(code, null, null, null);
        this.api().groups().create(request);

        // Act + Assert
        assertThatThrownBy(() -> this.api().groups().create(request)).isInstanceOfSatisfying(
            HttpClientErrorException.Conflict.class,
            exception -> assertThat(exception.getResponseBodyAsString()).contains("Group code already exists")
        );
    }

    @Test
    @DisplayName("lists groups with their children using offset pagination")
    void shouldListGroupsWithChildrenWhenOffsetPaginationIsRequested() {
        String leafCode = uniqueGroupCode("Leaf");
        String rootCode = uniqueGroupCode("Root");
        UUID leaf = this.groups.create(leafCode, "A descendant", "A descendant", Set.of(), Set.of());
        UUID root = this.groups.create(rootCode, "Root", "A parent", Set.of(leaf), Set.of());

        String response = this.api().get("/api/v0/groups?page=1&pageSize=100");

        assertThat(response)
            .contains("\"id\":\"" + root + "\"")
            .contains("\"code\":\"" + rootCode + "\"")
            .contains("\"displayName\":\"Root\"")
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

    private static String uniqueGroupCode(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
