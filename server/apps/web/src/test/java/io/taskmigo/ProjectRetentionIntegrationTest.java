package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.access.AccessService;
import io.taskmigo.access.PermissionCatalog;
import io.taskmigo.history.ProjectHistory;
import io.taskmigo.organization.OrganizationService;
import io.taskmigo.project.ProjectException;
import io.taskmigo.project.ProjectService;
import io.taskmigo.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
class ProjectRetentionIntegrationTest {

    private final OrganizationService organizations;
    private final UserService users;
    private final AccessService access;
    private final ProjectService projects;
    private final ProjectHistory history;

    ProjectRetentionIntegrationTest(
        OrganizationService organizations,
        UserService users,
        AccessService access,
        ProjectService projects,
        ProjectHistory history
    ) {
        this.organizations = organizations;
        this.users = users;
        this.access = access;
        this.projects = projects;
        this.history = history;
    }

    @Test
    void retentionDeletesExpiredArchivedProjectsAndTheirOwnedData() {
        UUID organization = this.organizations.create("retention-" + UUID.randomUUID(), "Retention");
        UUID user = this.users.create(
            organization,
            "retention-user-" + UUID.randomUUID(),
            Set.of(UUID.randomUUID() + "@example.com"),
            "Retention",
            "User"
        );
        UUID role = this.access.createRole(
            organization,
            "Retention Reader",
            null,
            Set.of(PermissionCatalog.PROJECT_READ)
        );
        UUID project = this.projects.create(
            organization,
            "retention-project-" + UUID.randomUUID(),
            "Retention Project",
            null
        );
        UUID member = this.projects.addMember(project, "USER", user);
        this.projects.setMemberRoles(project, member, Set.of(role));
        this.projects.archive(project);

        assertThat(this.projects.deleteArchivedBefore(Instant.now().minus(Duration.ofDays(30)))).isZero();
        assertThat(this.projects.deleteArchivedBefore(Instant.now().plus(Duration.ofDays(1)))).isEqualTo(1);
        assertThat(this.history.list(project, null, 10).items()).isEmpty();
        assertThatThrownBy(() -> this.projects.effectivePermissions(project, user))
            .isInstanceOf(ProjectException.class)
            .hasMessageContaining("Project not found");
    }
}
