package io.taskmigo.web.security;

import io.taskmigo.acl.AclPolicyRegistry;
import io.taskmigo.acl.AclPolicyRegistry.PolicySnapshot;
import io.taskmigo.acl.ApiAclEngine;
import io.taskmigo.acl.ApiAclEngine.ResponsePlan;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/// Adapts authenticated HTTP principals into the stable ACL context used by request and response policy evaluation.
@Component
public final class ApiAclSupport {

    private static final String POLICY_SNAPSHOT_ATTRIBUTE = ApiAclSupport.class.getName() + ".policySnapshot";
    private final AclPolicyRegistry policies;
    private final ApiAclEngine engine;

    ApiAclSupport(AclPolicyRegistry policies, ApiAclEngine engine) {
        this.policies = policies;
        this.engine = engine;
    }

    boolean isRequestAllowed(Authentication authentication, String method, String path) {
        Context context = this.context(authentication, method, path);
        PolicySnapshot snapshot = this.policySnapshot();
        return this.engine.isRequestAllowed(snapshot.requestPolicies(), method, path, context.values());
    }

    public ResponsePlan responsePlan(Authentication authentication, String method, String path) {
        Context context = this.context(authentication, method, path);
        PolicySnapshot snapshot = this.policySnapshot();
        return this.engine.planResponse(snapshot.responsePolicies(), method, path, context.values());
    }

    private PolicySnapshot policySnapshot() {
        RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
        Object existing = attributes.getAttribute(POLICY_SNAPSHOT_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (existing instanceof PolicySnapshot snapshot) return snapshot;

        PolicySnapshot snapshot = this.policies.snapshot();
        attributes.setAttribute(POLICY_SNAPSHOT_ATTRIBUTE, snapshot, RequestAttributes.SCOPE_REQUEST);
        return snapshot;
    }

    private Context context(Authentication authentication, String method, String path) {
        Map<String, Object> values = new HashMap<>();
        values.put("principal.id", authentication.getName());
        values.put("principal.type", "unknown");
        values.put("request.method", method);
        values.put("request.path", path);

        if (authentication instanceof JwtAuthenticationToken token) {
            String principalType = token.getToken().getClaimAsString("principal_type");
            if (principalType != null && !principalType.isBlank()) values.put("principal.type", principalType);
            String userIdClaim = token.getToken().getClaimAsString("user_id");
            if (userIdClaim != null && !userIdClaim.isBlank()) {
                values.put("principal.id", userIdClaim);
            }
        }
        return new Context(Map.copyOf(values));
    }

    private record Context(Map<String, Object> values) {}
}
