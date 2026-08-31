package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.access.AccessService;
import io.taskmigo.access.PermissionCatalog;
import io.taskmigo.foundation.CursorPage;
import io.taskmigo.group.GroupService;
import io.taskmigo.history.ProjectHistory;
import io.taskmigo.organization.OrganizationService;
import io.taskmigo.project.ProjectChanged;
import io.taskmigo.project.ProjectException;
import io.taskmigo.project.ProjectService;
import io.taskmigo.user.SystemUser;
import io.taskmigo.user.UserService;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
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
class ResourceModelIntegrationTest {

    private final OrganizationService organizations;
    private final UserService users;
    private final GroupService groups;
    private final AccessService access;
    private final ProjectService projects;
    private final ProjectHistory history;
    private final JdbcTemplate jdbc;
    private final Flyway flyway;

    ResourceModelIntegrationTest(
        OrganizationService organizations,
        UserService users,
        GroupService groups,
        AccessService access,
        ProjectService projects,
        ProjectHistory history,
        JdbcTemplate jdbc,
        Flyway flyway
    ) {
        this.organizations = organizations;
        this.users = users;
        this.groups = groups;
        this.access = access;
        this.projects = projects;
        this.history = history;
        this.jdbc = jdbc;
        this.flyway = flyway;
    }

    @Test
    void flywayBuildsTheSchemaAndHibernateOnlyValidatesIt() {
        var current = Objects.requireNonNull(this.flyway.info().current());
        assertThat(current.getVersion().getVersion()).isEqualTo("1");
        assertThat(
            this.jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)
        ).isEqualTo(1);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'project_members'",
                Integer.class
            )
        ).isEqualTo(1);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'project_history'",
                Integer.class
            )
        ).isEqualTo(1);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'user_emails'",
                Integer.class
            )
        ).isEqualTo(1);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'users' and column_name = 'is_system'",
                Integer.class
            )
        ).isZero();
    }

    @Test
    void usersCanBeOrganizationlessPasswordlessAndHaveZeroOrManyEmails() {
        String noEmailUsername = "no-email-" + UUID.randomUUID();
        UUID noEmail = this.users.create(null, noEmailUsername, Set.of(), "No", "Email");
        var noEmailInfo = this.users.require(noEmail);

        assertThat(noEmailInfo.organizationId()).isNull();
        assertThat(noEmailInfo.firstName()).isEqualTo("No");
        assertThat(noEmailInfo.lastName()).isEqualTo("Email");
        assertThat(noEmailInfo.displayName()).isEqualTo("No Email");
        assertThat(noEmailInfo.emails()).isEmpty();
        assertThat(this.users.findForAuthentication(noEmailUsername).orElseThrow().passwordHash()).isNull();

        String firstEmail = "First-" + UUID.randomUUID() + "@Example.COM";
        String secondEmail = "Second-" + UUID.randomUUID() + "@Example.COM";
        UUID manyEmails = this.users.create(
            null,
            "many-emails-" + UUID.randomUUID(),
            Set.of(firstEmail, secondEmail),
            "Many",
            "Emails"
        );

        assertThat(this.users.require(manyEmails).emails()).containsExactlyInAnyOrder(
            firstEmail.toLowerCase(Locale.ROOT),
            secondEmail.toLowerCase(Locale.ROOT)
        );
    }

    @Test
    void projectMutationsRecordStructuredHistoryWithStableCursorPagination() {
        UUID org = this.organizations.create("history-" + UUID.randomUUID(), "History Org");
        UUID user = this.users.create(
            org,
            "history-user-" + UUID.randomUUID(),
            Set.of(UUID.randomUUID() + "@example.com"),
            "History",
            "User"
        );
        var admin = new ProjectChanged.Actor(ProjectChanged.ActorType.USER, UUID.randomUUID().toString(), "Admin User");
        var self = new ProjectChanged.Actor(ProjectChanged.ActorType.USER, user.toString(), "History User");
        UUID project = this.projects.create(
            org,
            "history-project-" + UUID.randomUUID(),
            "History Project",
            null,
            admin
        );
        UUID role = this.access.createRole(org, "History Role", null, Set.of(PermissionCatalog.PROJECT_READ));
        UUID member = this.projects.addMember(project, "USER", user, self);
        this.projects.setMemberRoles(project, member, Set.of(role), admin);
        this.projects.removeMember(project, member, admin);
        this.projects.archive(project, admin);

        CursorPage<ProjectHistory.Entry> first = this.history.list(project, null, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isNotNull();
        assertThat(first.items().getFirst().action()).isEqualTo(ProjectChanged.Action.PROJECT_ARCHIVED);
        assertThat(first.items().get(1).action()).isEqualTo(ProjectChanged.Action.MEMBER_REMOVED);
        assertThat(first.items().get(1).actor().displayName()).isEqualTo("Admin User");
        ProjectChanged.Target removedTarget = Objects.requireNonNull(first.items().get(1).target());
        assertThat(removedTarget.displayName()).isEqualTo("History User");

        CursorPage<ProjectHistory.Entry> second = this.history.list(
            project,
            Objects.requireNonNull(first.nextCursor()),
            2
        );
        assertThat(second.items())
            .extracting(ProjectHistory.Entry::action)
            .containsExactly(ProjectChanged.Action.MEMBER_ROLES_CHANGED, ProjectChanged.Action.MEMBER_JOINED);
        assertThat(second.items().getFirst().changes())
            .singleElement()
            .satisfies(change -> {
                assertThat(change.field()).isEqualTo("roles");
                assertThat(change.before()).isInstanceOf(List.class);
                assertThat(change.after()).isInstanceOf(List.class);
            });
        CursorPage<ProjectHistory.Entry> third = this.history.list(
            project,
            Objects.requireNonNull(second.nextCursor()),
            2
        );
        assertThat(third.items())
            .extracting(ProjectHistory.Entry::action)
            .containsExactly(ProjectChanged.Action.PROJECT_CREATED);
        assertThat(third.nextCursor()).isNull();
    }

    @Test
    void externalGroupAccessIsDerivedLiveAndAdditive() {
        UUID customer = this.organizations.create("customer-" + UUID.randomUUID(), "Customer");
        UUID vendor = this.organizations.create("vendor-" + UUID.randomUUID(), "Vendor");
        UUID engineer = this.users.create(
            vendor,
            "engineer-" + UUID.randomUUID(),
            Set.of(UUID.randomUUID() + "@example.com"),
            "External",
            "Engineer"
        );
        UUID vendorGroup = this.groups.create(vendor, "Backend", null);
        UUID project = this.projects.create(customer, "alpha-" + UUID.randomUUID(), "Alpha", null);
        UUID directRole = this.access.createRole(customer, "Reader", null, Set.of(PermissionCatalog.PROJECT_READ));
        UUID groupRole = this.access.createRole(
            customer,
            "Member manager",
            null,
            Set.of(PermissionCatalog.PROJECT_MEMBERS_MANAGE)
        );

        this.groups.addMember(vendorGroup, engineer);
        UUID directMembership = this.projects.addMember(project, "USER", engineer);
        this.projects.setMemberRoles(project, directMembership, Set.of(directRole));
        UUID groupMembership = this.projects.addMember(project, "GROUP", vendorGroup);
        this.projects.setMemberRoles(project, groupMembership, Set.of(groupRole));

        assertThat(this.projects.effectivePermissions(project, engineer)).containsExactlyInAnyOrder(
            PermissionCatalog.PROJECT_READ,
            PermissionCatalog.PROJECT_MEMBERS_MANAGE
        );
        this.groups.removeMember(vendorGroup, engineer);
        assertThat(this.projects.effectivePermissions(project, engineer)).containsExactly(
            PermissionCatalog.PROJECT_READ
        );
    }

    @Test
    void systemUserAlwaysHasEveryProjectPermissionWithoutMembership() {
        UUID organization = this.organizations.create("system-access-" + UUID.randomUUID(), "System Access");
        UUID project = this.projects.create(
            organization,
            "system-project-" + UUID.randomUUID(),
            "System Project",
            null
        );
        UUID systemUser = this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().id();

        assertThat(this.projects.effectivePermissions(project, systemUser)).containsExactlyInAnyOrderElementsOf(
            PermissionCatalog.ALL
        );
    }

    @Test
    void projectRoleOrganizationInvariantIsRejected() {
        UUID orgA = this.organizations.create("org-a-" + UUID.randomUUID(), "A");
        UUID orgB = this.organizations.create("org-b-" + UUID.randomUUID(), "B");
        UUID userB = this.users.create(
            orgB,
            "user-b-" + UUID.randomUUID(),
            Set.of(UUID.randomUUID() + "@example.com"),
            "B",
            "User"
        );
        UUID projectA = this.projects.create(orgA, "p-" + UUID.randomUUID(), "Project", null);
        UUID member = this.projects.addMember(projectA, "USER", userB);
        UUID roleB = this.access.createRole(orgB, "Vendor Role", null, Set.of(PermissionCatalog.PROJECT_READ));
        assertThatThrownBy(() -> this.projects.setMemberRoles(projectA, member, Set.of(roleB)))
            .isInstanceOf(ProjectException.class)
            .hasMessageContaining("Project Organization");
    }

    @Test
    void archivedProjectIsReadOnlyForMembershipMutations() {
        UUID org = this.organizations.create("archive-" + UUID.randomUUID(), "Archive Org");
        UUID user = this.users.create(
            org,
            "archive-user-" + UUID.randomUUID(),
            Set.of(UUID.randomUUID() + "@example.com"),
            "Archive",
            "User"
        );
        UUID project = this.projects.create(org, "archive-project-" + UUID.randomUUID(), "Archive Project", null);
        this.projects.archive(project);
        assertThatThrownBy(() -> this.projects.addMember(project, "USER", user))
            .isInstanceOf(ProjectException.class)
            .hasMessageContaining("read-only");
    }
}
