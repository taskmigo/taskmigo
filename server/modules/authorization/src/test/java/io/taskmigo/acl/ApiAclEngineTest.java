package io.taskmigo.acl;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.acl.AclExpression.Eq;
import io.taskmigo.acl.AclExpression.Literal;
import io.taskmigo.acl.AclExpression.Ref;
import io.taskmigo.acl.AclExpression.Relation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiAclEngineTest {

    private final ApiAclEngine engine = new ApiAclEngine();

    @Test
    void systemRequestRulesCannotBeBypassedByCustomAllow() {
        var target = new ApiTarget(Set.of("GET"), "/api/v0/projects/**");
        var system = new RequestAclPolicy(
            "same-organization",
            RequestAclPolicy.Origin.SYSTEM,
            target,
            List.of(new RequestAclPolicy.Rule(
                "same-organization",
                RequestAclPolicy.Effect.ALLOW,
                new Eq(new Ref("principal.organizationId"), new Ref("request.organizationId"))
            ))
        );
        var custom = new RequestAclPolicy(
            "allow-everything",
            RequestAclPolicy.Origin.CUSTOM,
            target,
            List.of(new RequestAclPolicy.Rule(
                "allow",
                RequestAclPolicy.Effect.ALLOW,
                new Eq(new Literal(true), new Literal(true))
            ))
        );

        assertThat(engine.isRequestAllowed(
            List.of(system, custom),
            "GET",
            "/api/v0/projects/123",
            Map.of("principal.organizationId", "org-a", "request.organizationId", "org-b")
        )).isFalse();
    }

    @Test
    void responsePlanSpecializesPrincipalButKeepsObjectReferenceForDatabaseTranslation() {
        UUID userId = UUID.randomUUID();
        var policy = new ResponseAclPolicy(
            "project-members",
            ResponseAclPolicy.Origin.CUSTOM,
            new ApiTarget(Set.of("GET"), "/api/v0/projects/**"),
            List.of(new ResponseAclPolicy.Rule(
                "member",
                ResponseAclPolicy.Effect.ALLOW,
                new Relation("projectMember", new Ref("principal.id"), new Ref("object.id")),
                ResponseAclPolicy.FieldSelection.only(Set.of("id", "name"))
            ))
        );

        var plan = engine.planResponse(
            List.of(policy),
            "GET",
            "/api/v0/projects/123",
            Map.of("principal.id", userId)
        );

        assertThat(plan.objectPredicate().toString()).contains(userId.toString()).contains("object.id");
        assertThat(plan.fields().fields()).containsExactlyInAnyOrder("id", "name");
    }

    @Test
    void aclCannotTargetNonApiPaths() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ApiTarget(Set.of("GET"), "/internal/projects/**"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
