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
class ResourceModelIntegrationTest {

    @Autowired
    ResourceService resources;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Flyway flyway;

    @Test
    void flywayBuildsTheSchemaAndHibernateOnlyValidatesIt() {
        var current = Objects.requireNonNull(this.flyway.info().current());
        assertThat(current.getVersion().getVersion()).isEqualTo("2");
        assertThat(
            this.jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)
        ).isEqualTo(2);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'project_members'",
                Integer.class
            )
        ).isEqualTo(1);
    }

    @Test
    void flywayTriggerRejectsCrossOrganizationGroupMembershipEvenOutsideJpa() {
        UUID orgA = this.resources.createOrganization("db-org-a-" + UUID.randomUUID(), "DB A");
        UUID orgB = this.resources.createOrganization("db-org-b-" + UUID.randomUUID(), "DB B");
        UUID userB = this.resources.createUser(
            orgB,
            "db-user-b-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "DB User"
        );
        UUID groupA = this.resources.createGroup(orgA, "DB Group", null);
        assertThatThrownBy(() ->
            this.jdbc.update("insert into group_members(group_id, user_id) values (?, ?)", groupA, userB)
        ).hasMessageContaining("Group members must belong to the Group organization");
    }

    @Test
    void externalGroupAccessIsDerivedLiveAndAdditive() {
        UUID customer = this.resources.createOrganization("customer-" + UUID.randomUUID(), "Customer");
        UUID vendor = this.resources.createOrganization("vendor-" + UUID.randomUUID(), "Vendor");
        UUID engineer = this.resources.createUser(
            vendor,
            "engineer-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "Engineer"
        );
        UUID vendorGroup = this.resources.createGroup(vendor, "Backend", null);
        UUID project = this.resources.createProject(customer, "alpha-" + UUID.randomUUID(), "Alpha", null);
        UUID directRole = this.resources.createRole(customer, "Reader", null, Set.of(PermissionCatalog.PROJECT_READ));
        UUID groupRole = this.resources.createRole(
            customer,
            "Member manager",
            null,
            Set.of(PermissionCatalog.PROJECT_MEMBERS_MANAGE)
        );

        this.resources.addGroupMember(vendorGroup, engineer);
        UUID directMembership = this.resources.addProjectMember(project, "USER", engineer);
        this.resources.setProjectMemberRoles(project, directMembership, Set.of(directRole));
        UUID groupMembership = this.resources.addProjectMember(project, "GROUP", vendorGroup);
        this.resources.setProjectMemberRoles(project, groupMembership, Set.of(groupRole));

        assertThat(this.resources.effectivePermissions(project, engineer)).containsExactlyInAnyOrder(
            PermissionCatalog.PROJECT_READ,
            PermissionCatalog.PROJECT_MEMBERS_MANAGE
        );

        this.resources.removeGroupMember(vendorGroup, engineer);
        assertThat(this.resources.effectivePermissions(project, engineer)).containsExactly(
            PermissionCatalog.PROJECT_READ
        );
    }

    @Test
    void groupMembershipAndProjectRoleOrganizationInvariantsAreRejected() {
        UUID orgA = this.resources.createOrganization("org-a-" + UUID.randomUUID(), "A");
        UUID orgB = this.resources.createOrganization("org-b-" + UUID.randomUUID(), "B");
        UUID userB = this.resources.createUser(
            orgB,
            "user-b-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "B User"
        );
        UUID groupA = this.resources.createGroup(orgA, "A Group", null);
        assertThatThrownBy(() -> this.resources.addGroupMember(groupA, userB))
            .isInstanceOf(ResourceException.class)
            .hasMessageContaining("owning Organization");

        UUID projectA = this.resources.createProject(orgA, "p-" + UUID.randomUUID(), "Project", null);
        UUID member = this.resources.addProjectMember(projectA, "USER", userB);
        UUID roleB = this.resources.createRole(orgB, "Vendor Role", null, Set.of(PermissionCatalog.PROJECT_READ));
        assertThatThrownBy(() -> this.resources.setProjectMemberRoles(projectA, member, Set.of(roleB)))
            .isInstanceOf(ResourceException.class)
            .hasMessageContaining("Project Organization");
    }

    @Test
    void archivedProjectIsReadOnlyForMembershipMutations() {
        UUID org = this.resources.createOrganization("archive-" + UUID.randomUUID(), "Archive Org");
        UUID user = this.resources.createUser(
            org,
            "archive-user-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "Archive User"
        );
        UUID project = this.resources.createProject(
            org,
            "archive-project-" + UUID.randomUUID(),
            "Archive Project",
            null
        );
        this.resources.archiveProject(project);
        assertThatThrownBy(() -> this.resources.addProjectMember(project, "USER", user))
            .isInstanceOf(ResourceException.class)
            .hasMessageContaining("read-only");
    }
}
