package io.taskmigo.web.security;

import io.taskmigo.acl.AclPolicyRegistry;
import io.taskmigo.acl.ApiAclEngine;
import io.taskmigo.acl.ApiAclEngine.ResponsePlan;
import io.taskmigo.user.UserService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/// Adapts authenticated HTTP principals into the stable ACL context used by request and response policy evaluation.
@Component
public final class ApiAclSupport {

    private final AclPolicyRegistry policies;
    private final ApiAclEngine engine;
    private final UserService users;

    ApiAclSupport(AclPolicyRegistry policies, ApiAclEngine engine, UserService users) {
        this.policies = policies;
        this.engine = engine;
        this.users = users;
    }

    boolean isRequestAllowed(Authentication authentication, String method, String path) {
        Context context = this.context(authentication, method, path);
        return this.engine.isRequestAllowed(this.policies.requestPolicies(context.organizationId()), method, path, context.values());
    }

    public ResponsePlan responsePlan(Authentication authentication, String method, String path) {
        Context context = this.context(authentication, method, path);
        return this.engine.planResponse(this.policies.responsePolicies(context.organizationId()), method, path, context.values());
    }

    public void requireOrganization(Authentication authentication, UUID organizationId) {
        Context context = this.context(authentication, "ACL_MANAGEMENT", "/api/v0/acl-management");
        if (!organizationId.equals(context.organizationId())) {
            throw new AccessDeniedException("ACL policies can only be managed for the principal organization");
        }
    }

    private Context context(Authentication authentication, String method, String path) {
        Map<String, Object> values = new HashMap<>();
        values.put("principal.id", authentication.getName());
        values.put("principal.type", "unknown");
        values.put("request.method", method);
        values.put("request.path", path);

        UUID organizationId = null;
        if (authentication instanceof JwtAuthenticationToken token) {
            String principalType = token.getToken().getClaimAsString("principal_type");
            if (principalType != null && !principalType.isBlank()) values.put("principal.type", principalType);
            String userIdClaim = token.getToken().getClaimAsString("user_id");
            if (userIdClaim != null && !userIdClaim.isBlank()) {
                UUID userId = UUID.fromString(userIdClaim);
                UserService.UserInfo user = this.users.require(userId);
                values.put("principal.id", userId);
                organizationId = user.organizationId();
                if (organizationId != null) values.put("principal.organizationId", organizationId);
            }
        }
        return new Context(organizationId, Map.copyOf(values));
    }

    private record Context(@Nullable UUID organizationId, Map<String, Object> values) {}
}
