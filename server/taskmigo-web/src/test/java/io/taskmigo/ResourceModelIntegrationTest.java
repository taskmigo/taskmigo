package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.resource.PermissionCatalog;
import io.taskmigo.resource.ResourceException;
import io.taskmigo.resource.ResourceService;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "taskmigo.security.internal-clients.cli.client-id=integration-client",
        "taskmigo.security.internal-clients.cli.client-secret=integration-secret",
        "taskmigo.security.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
class ResourceModelIntegrationTest {

    @Autowired
    ResourceService resources;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Flyway flyway;

    @Test
    void flywayBuildsTheSchemaAndHibernateOnlyValidatesIt() {
        var current = Objects.requireNonNull(flyway.info().current());
        assertThat(current.getVersion().getVersion()).isEqualTo("2");
        assertThat(
            jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)
        ).isEqualTo(2);
        assertThat(
            jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'project_members'",
                Integer.class
            )
        ).isEqualTo(1);
    }

    @Test
    void flywayTriggerRejectsCrossOrganizationGroupMembershipEvenOutsideJpa() {
        UUID orgA = resources.createOrganization("db-org-a-" + UUID.randomUUID(), "DB A");
        UUID orgB = resources.createOrganization("db-org-b-" + UUID.randomUUID(), "DB B");
        UUID userB = resources.createUser(
            orgB,
            "db-user-b-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "DB User"
        );
        UUID groupA = resources.createGroup(orgA, "DB Group", null);
        assertThatThrownBy(() ->
            jdbc.update("insert into group_members(group_id, user_id) values (?, ?)", groupA, userB)
        ).hasMessageContaining("Group members must belong to the Group organization");
    }

    @Test
    void externalGroupAccessIsDerivedLiveAndAdditive() {
        UUID customer = resources.createOrganization("customer-" + UUID.randomUUID(), "Customer");
        UUID vendor = resources.createOrganization("vendor-" + UUID.randomUUID(), "Vendor");
        UUID engineer = resources.createUser(
            vendor,
            "engineer-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "Engineer"
        );
        UUID vendorGroup = resources.createGroup(vendor, "Backend", null);
        UUID project = resources.createProject(customer, "alpha-" + UUID.randomUUID(), "Alpha", null);
        UUID directRole = resources.createRole(customer, "Reader", null, Set.of(PermissionCatalog.PROJECT_READ));
        UUID groupRole = resources.createRole(
            customer,
            "Member manager",
            null,
            Set.of(PermissionCatalog.PROJECT_MEMBERS_MANAGE)
        );

        resources.addGroupMember(vendorGroup, engineer);
        UUID directMembership = resources.addProjectMember(project, "USER", engineer);
        resources.setProjectMemberRoles(project, directMembership, Set.of(directRole));
        UUID groupMembership = resources.addProjectMember(project, "GROUP", vendorGroup);
        resources.setProjectMemberRoles(project, groupMembership, Set.of(groupRole));

        assertThat(resources.effectivePermissions(project, engineer)).containsExactlyInAnyOrder(
            PermissionCatalog.PROJECT_READ,
            PermissionCatalog.PROJECT_MEMBERS_MANAGE
        );

        resources.removeGroupMember(vendorGroup, engineer);
        assertThat(resources.effectivePermissions(project, engineer)).containsExactly(PermissionCatalog.PROJECT_READ);
    }

    @Test
    void groupMembershipAndProjectRoleOrganizationInvariantsAreRejected() {
        UUID orgA = resources.createOrganization("org-a-" + UUID.randomUUID(), "A");
        UUID orgB = resources.createOrganization("org-b-" + UUID.randomUUID(), "B");
        UUID userB = resources.createUser(
            orgB,
            "user-b-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "B User"
        );
        UUID groupA = resources.createGroup(orgA, "A Group", null);
        assertThatThrownBy(() -> resources.addGroupMember(groupA, userB))
            .isInstanceOf(ResourceException.class)
            .hasMessageContaining("owning Organization");

        UUID projectA = resources.createProject(orgA, "p-" + UUID.randomUUID(), "Project", null);
        UUID member = resources.addProjectMember(projectA, "USER", userB);
        UUID roleB = resources.createRole(orgB, "Vendor Role", null, Set.of(PermissionCatalog.PROJECT_READ));
        assertThatThrownBy(() -> resources.setProjectMemberRoles(projectA, member, Set.of(roleB)))
            .isInstanceOf(ResourceException.class)
            .hasMessageContaining("Project Organization");
    }

    @Test
    void archivedProjectIsReadOnlyForMembershipMutations() {
        UUID org = resources.createOrganization("archive-" + UUID.randomUUID(), "Archive Org");
        UUID user = resources.createUser(
            org,
            "archive-user-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "Archive User"
        );
        UUID project = resources.createProject(org, "archive-project-" + UUID.randomUUID(), "Archive Project", null);
        resources.archiveProject(project);
        assertThatThrownBy(() -> resources.addProjectMember(project, "USER", user))
            .isInstanceOf(ResourceException.class)
            .hasMessageContaining("read-only");
    }
}
