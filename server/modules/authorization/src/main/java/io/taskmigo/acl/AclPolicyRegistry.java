package io.taskmigo.acl;

import io.taskmigo.acl.AclExpression.Exists;
import io.taskmigo.acl.AclExpression.Ref;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/// Provides the immutable system ACL policies used by HTTP request and response evaluation.
@Service
public class AclPolicyRegistry {

    private static final Set<String> API_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private final List<RequestAclPolicy> systemRequests = List.of(
        new RequestAclPolicy(
            "system/api-authenticated",
            RequestAclPolicy.Origin.SYSTEM,
            new ApiTarget(API_METHODS, "/api/v0/**"),
            List.of(
                new RequestAclPolicy.Rule(
                    "authenticated-principal",
                    RequestAclPolicy.Effect.ALLOW,
                    new Exists(new Ref("principal.id"))
                )
            )
        )
    );

    public PolicySnapshot snapshot() {
        return new PolicySnapshot(this.systemRequests, List.of());
    }

    public record PolicySnapshot(List<RequestAclPolicy> requestPolicies, List<ResponseAclPolicy> responsePolicies) {}
}
