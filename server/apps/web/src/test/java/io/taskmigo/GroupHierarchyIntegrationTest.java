package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.group.GroupException;
import io.taskmigo.auth.group.GroupService;
import io.taskmigo.auth.role.RoleException;
import io.taskmigo.auth.role.RoleInfo;
import io.taskmigo.auth.role.RoleService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-id=integration-client",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-secret=integration-secret",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-authentication-methods=client_secret_basic",
        "spring.security.oauth2.authorizationserver.client.cli.registration.authorization-grant-types=client_credentials",
        "spring.security.oauth2.authorizationserver.client.cli.registration.scopes=taskmigo.api",
        "taskmigo.security.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GroupHierarchyIntegrationTest {

    private final GroupService groups;
    private final RoleService access;
    private final JdbcTemplate jdbc;

    GroupHierarchyIntegrationTest(GroupService groups, RoleService access, JdbcTemplate jdbc) {
        this.groups = groups;
        this.access = access;
        this.jdbc = jdbc;
    }

    /**
     * Verifies that a Group closure contains self and multi-level relationships while direct edges stay unique.
     *
     * Given: a Group hierarchy with a three-level branch and duplicate child and Role assignments.
     * Expect: effective Roles and closure rows include every reachable node exactly once.
     */
    @Test
    @DisplayName("persists unique group edges and resolves descendant roles")
    void shouldResolveDescendantRolesWhenUniqueGroupEdgesArePersisted() {
        UUID employee = role("EmployeeRole");
        UUID developer = role("DeveloperRole");
        UUID manager = role("ManagerRole");
        this.access.setChildRoles(developer, Set.of(employee));

        UUID engineering = group("Engineering");
        UUID backend = group("Backend");
        UUID platform = group("Platform");
        UUID frontend = group("Frontend");
        this.groups.setChildGroups(engineering, List.of(frontend, backend, backend));
        this.groups.setChildGroups(backend, Set.of(platform));
        this.groups.setRoles(engineering, Set.of(manager));
        this.groups.setRoles(platform, List.of(developer, developer));
        this.groups.setRoles(frontend, Set.of(employee));

        assertThat(this.groups.effectiveRoles(engineering))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(employee, developer, manager).stream().sorted().toList());
        assertThat(this.groups.effectiveRoles(backend))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(employee, developer).stream().sorted().toList());
        assertThat(this.groups.effectiveRoles(frontend)).extracting(RoleInfo::id).containsExactly(employee);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from group_hierarchy where parent_group_id = ? and child_group_id = ?",
                Integer.class,
                engineering,
                backend
            )
        ).isEqualTo(1);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from group_hierarchy_closure where ancestor_group_id = ? and descendant_group_id = ?",
                Integer.class,
                engineering,
                engineering
            )
        ).isEqualTo(1);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from group_hierarchy_closure where ancestor_group_id = ? and descendant_group_id = ?",
                Integer.class,
                engineering,
                platform
            )
        ).isEqualTo(1);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from group_roles where group_id = ? and role_id = ?",
                Integer.class,
                platform,
                developer
            )
        ).isEqualTo(1);
    }

    /**
     * Verifies that rejected cycle mutations leave both direct hierarchy and closure data unchanged.
     *
     * Given: a valid root-to-leaf Group chain followed by two invalid cycle replacements.
     * Expect: the original transitive Role relationship remains effective after both failures.
     */
    @Test
    @DisplayName("rejects group cycles without changing existing edges")
    void shouldPreserveExistingEdgesWhenGroupCycleIsRejected() {
        UUID root = group("RootGroup");
        UUID child = group("ChildGroup");
        UUID leaf = group("LeafGroup");
        UUID role = role("LeafRole");
        this.groups.setChildGroups(root, Set.of(child));
        this.groups.setChildGroups(child, Set.of(leaf));
        this.groups.setRoles(leaf, Set.of(role));

        assertThatThrownBy(() -> this.groups.setChildGroups(root, Set.of(root)))
            .isInstanceOf(GroupException.class)
            .hasMessageContaining("Group hierarchy must be acyclic");
        assertThatThrownBy(() -> this.groups.setChildGroups(leaf, Set.of(root)))
            .isInstanceOf(GroupException.class)
            .hasMessageContaining("Group hierarchy must be acyclic");

        assertThat(this.groups.effectiveRoles(root)).extracting(RoleInfo::id).containsExactly(role);
        assertThat(this.groups.effectiveRoles(leaf)).extracting(RoleInfo::id).containsExactly(role);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from group_hierarchy_closure where ancestor_group_id = ? and descendant_group_id = ?",
                Integer.class,
                root,
                leaf
            )
        ).isEqualTo(1);
    }

    /**
     * Verifies that invalid Group and Role assignments fail before changing the existing hierarchy.
     *
     * Given: a valid Group-to-Role assignment followed by unknown relationship ids.
     * Expect: validation errors are raised and the original effective Role remains available.
     */
    @Test
    @DisplayName("rejects unknown group relationships without changing existing edges")
    void shouldPreserveExistingEdgesWhenGroupRelationshipIsUnknown() {
        UUID root = group("RootGroup");
        UUID child = group("ChildGroup");
        UUID role = role("RoleName");
        this.groups.setChildGroups(root, Set.of(child));
        this.groups.setRoles(child, Set.of(role));

        assertThatThrownBy(() -> this.groups.setChildGroups(root, Set.of(UUID.randomUUID())))
            .isInstanceOf(GroupException.class)
            .hasMessage("One or more child Groups do not exist");
        assertThatThrownBy(() -> this.groups.setRoles(child, Set.of(UUID.randomUUID())))
            .isInstanceOf(RoleException.class)
            .hasMessage("One or more Roles do not exist");

        assertThat(this.groups.effectiveRoles(root)).extracting(RoleInfo::id).containsExactly(role);
    }

    private UUID group(String name) {
        return this.groups.create(name, null);
    }

    private UUID role(String name) {
        return this.access.createRole(name + UUID.randomUUID().toString().replace("-", ""), null, Set.of());
    }
}
