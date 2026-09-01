package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.AuthorizationEngine;
import io.taskmigo.authorization.AuthorizationResource.Effect;
import io.taskmigo.authorization.AuthorizationResource.FieldRule;
import io.taskmigo.authorization.AuthorizationResource.Match;
import io.taskmigo.authorization.AuthorizationResource.Origin;
import io.taskmigo.authorization.AuthorizationResource.Role;
import io.taskmigo.authorization.AuthorizationResource.Statement;
import io.taskmigo.authorization.AuthorizationResource.Target;
import io.taskmigo.authorization.AuthorizationResourceService;
import io.taskmigo.authorization.EffectiveAuthorizationResolver;
import io.taskmigo.organization.OrganizationService;
import io.taskmigo.project.ProjectAclQueryService;
import io.taskmigo.project.ProjectService;
import io.taskmigo.user.UserService;
import java.util.List;
import java.util.Map;
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
class AuthorizationIntegrationTest {

    private final OrganizationService organizations;
    private final UserService users;
    private final ProjectService projects;
    private final ProjectAclQueryService aclProjects;
    private final AuthorizationResourceService resources;
    private final EffectiveAuthorizationResolver resolver;
    private final AuthorizationEngine engine;

    AuthorizationIntegrationTest(
        OrganizationService organizations,
        UserService users,
        ProjectService projects,
        ProjectAclQueryService aclProjects,
        AuthorizationResourceService resources,
        EffectiveAuthorizationResolver resolver,
        AuthorizationEngine engine
    ) {
        this.organizations = organizations;
        this.users = users;
        this.projects = projects;
        this.aclProjects = aclProjects;
        this.resources = resources;
        this.resolver = resolver;
        this.engine = engine;
    }

    @Test
    void persistedRoleFiltersProjectsInDatabaseAndPreservesProvenance() {
        UUID organization = this.organizations.create("auth-" + UUID.randomUUID(), "Authorization Org");
        UUID otherOrganization = this.organizations.create("auth-other-" + UUID.randomUUID(), "Other Authorization Org");
        UUID user = this.users.create(
            organization,
            "auth-user-" + UUID.randomUUID(),
            Set.of(UUID.randomUUID() + "@example.com"),
            "Authorization",
            "User"
        );
        UUID visibleProject = this.projects.create(
            organization,
            "visible-" + UUID.randomUUID(),
            "Visible",
            "sensitive description"
        );
        this.projects.create(otherOrganization, "foreign-" + UUID.randomUUID(), "Foreign", null);

        String statementKey = "test.project.read." + UUID.randomUUID();
        String roleKey = "test.project.reader." + UUID.randomUUID();
        this.resources.upsertStatement(
                organization,
                new Statement(
                    statementKey,
                    "Read organization projects",
                    null,
                    new Match("GET", "/api/v0/projects"),
                    Target.OBJECT,
                    Effect.ALLOW,
                    "object.organizationId == principal.organizationId",
                    List.of(new FieldRule(Effect.DENY, List.of("description"), null))
                ),
                Origin.CUSTOM
            );
        this.resources.upsertRole(
                organization,
                new Role(roleKey, "Project reader", null, List.of(statementKey), List.of()),
                Origin.CUSTOM
            );
        this.resources.assignRole(user, roleKey);

        var authorization = this.resolver.resolve(user);
        var plan = this.engine.planObjects(
            authorization,
            "GET",
            "/api/v0/projects",
            Map.of(
                "principal.id",
                user,
                "principal.organizationId",
                organization,
                "principal.type",
                "user"
            )
        );
        List<ProjectAclQueryService.ProjectView> visible = this.aclProjects.list(plan);

        assertThat(visible).extracting(ProjectAclQueryService.ProjectView::id).containsExactly(visibleProject);
        assertThat(
            this.engine.authorizeFields(
                    plan,
                    visible.getFirst().authorizationContext(),
                    ProjectAclQueryService.ProjectView.FIELDS
                )
                .hiddenFields()
        ).containsExactly("description");
        assertThat(plan.decision().provenance().getOrDefault(statementKey, List.of()))
            .anySatisfy(provenance ->
                assertThat(provenance.path()).contains("role:" + roleKey, "statement:" + statementKey)
            );
    }
}
