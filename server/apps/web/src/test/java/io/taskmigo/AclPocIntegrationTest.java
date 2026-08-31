package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.acl.AclPolicyRegistry;
import io.taskmigo.acl.ApiAclEngine;
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
class AclPocIntegrationTest {

    private final OrganizationService organizations;
    private final UserService users;
    private final ProjectService projects;
    private final ProjectAclQueryService aclProjects;
    private final AclPolicyRegistry policies;
    private final ApiAclEngine engine;

    AclPocIntegrationTest(
        OrganizationService organizations,
        UserService users,
        ProjectService projects,
        ProjectAclQueryService aclProjects,
        AclPolicyRegistry policies,
        ApiAclEngine engine
    ) {
        this.organizations = organizations;
        this.users = users;
        this.projects = projects;
        this.aclProjects = aclProjects;
        this.policies = policies;
        this.engine = engine;
    }

    @Test
    void responseAclFiltersProjectsInDatabaseAndRestrictsFields() {
        UUID organization = this.organizations.create("acl-" + UUID.randomUUID(), "ACL Org");
        UUID otherOrganization = this.organizations.create("acl-other-" + UUID.randomUUID(), "Other ACL Org");
        UUID user = this.users.create(
            organization,
            "acl-user-" + UUID.randomUUID(),
            Set.of(UUID.randomUUID() + "@example.com"),
            "ACL",
            "User"
        );
        UUID visibleProject = this.projects.create(organization, "visible-" + UUID.randomUUID(), "Visible", "secret");
        this.projects.create(organization, "hidden-" + UUID.randomUUID(), "Not a member", null);
        this.projects.create(otherOrganization, "foreign-" + UUID.randomUUID(), "Foreign", null);
        this.projects.addMember(visibleProject, "USER", user);

        this.policies.upsertCustom(organization, "member-projects", responsePolicy());
        Map<String, Object> context = Map.of(
            "principal.id",
            user,
            "principal.organizationId",
            organization,
            "principal.type",
            "user"
        );
        var plan = this.engine.planResponse(
            this.policies.responsePolicies(organization),
            "GET",
            "/api/v0/projects",
            context
        );

        assertThat(this.aclProjects.list(plan))
            .extracting(ProjectAclQueryService.ProjectView::id)
            .containsExactly(visibleProject);
        assertThat(plan.fields().allows("id")).isTrue();
        assertThat(plan.fields().allows("name")).isTrue();
        assertThat(plan.fields().allows("description")).isFalse();
    }

    @Test
    void customRequestDenyIsEvaluatedAlongsideImmutableSystemRules() {
        UUID organization = UUID.randomUUID();
        this.policies.upsertCustom(organization, "block-archive", requestPolicy());
        Map<String, Object> context = Map.of("principal.id", UUID.randomUUID(), "principal.type", "user");

        assertThat(
            this.engine.isRequestAllowed(
                this.policies.requestPolicies(organization),
                "PATCH",
                "/api/v0/projects/123/archive",
                context
            )
        ).isFalse();
        assertThat(this.policies.customPolicyNames(organization)).containsExactly("block-archive");
        assertThat(this.policies.requestPolicies(organization)).anyMatch(policy ->
            policy.name().equals("system/api-authenticated")
        );
    }

    private static Map<String, Object> responsePolicy() {
        return Map.of(
            "kind",
            "acl/response",
            "spec",
            Map.of(
                "target",
                Map.of("methods", List.of("GET"), "path", "/api/v0/projects"),
                "rules",
                Map.of(
                    "member",
                    Map.of(
                        "effect",
                        "allow",
                        "when",
                        Map.of(
                            "relation",
                            Map.of("name", "projectMember", "principal", "principal.id", "object", "object.id")
                        ),
                        "fields",
                        Map.of("allow", List.of("id", "name"))
                    )
                )
            )
        );
    }

    private static Map<String, Object> requestPolicy() {
        return Map.of(
            "kind",
            "acl/request",
            "spec",
            Map.of(
                "target",
                Map.of("methods", List.of("PATCH"), "path", "/api/v0/projects/**"),
                "rules",
                Map.of(
                    "deny-all",
                    Map.of("effect", "deny", "when", Map.of("eq", List.of("principal.id", "principal.id")))
                )
            )
        );
    }
}
