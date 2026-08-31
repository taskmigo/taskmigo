package io.taskmigo.web.security;

import io.taskmigo.access.AccessService;
import io.taskmigo.acl.AclPolicyRegistry;
import io.taskmigo.acl.AclPolicyRegistry.PolicySnapshot;
import io.taskmigo.acl.AclStatement;
import io.taskmigo.acl.ApiAclEngine;
import io.taskmigo.acl.ApiAclEngine.ResponsePlan;
import io.taskmigo.identity.ServicePrincipalPermissions;
import io.taskmigo.user.SystemUser;
import io.taskmigo.user.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/// Resolves database-backed identity, ACL guardrails, and effective Role Statements for one HTTP request.
@Component
public final class ApiAclSupport {

    private static final String AUTHORIZATION_SNAPSHOT_ATTRIBUTE =
        ApiAclSupport.class.getName() + ".authorizationSnapshot";
    private static final String SYSTEM_RESOURCES_MANAGE_AUTHORITY =
        "PERMISSION_" + ServicePrincipalPermissions.SYSTEM_RESOURCES_MANAGE;

    private final AclPolicyRegistry policies;
    private final AccessService access;
    private final ApiAclEngine engine;
    private final UserService users;

    ApiAclSupport(AclPolicyRegistry policies, AccessService access, ApiAclEngine engine, UserService users) {
        this.policies = policies;
        this.access = access;
        this.engine = engine;
        this.users = users;
    }

    boolean isRequestAllowed(Authentication authentication, String method, String path) {
        Context context = this.context(authentication, method, path);
        AuthorizationSnapshot snapshot = this.authorizationSnapshot(context);
        return this.engine.isRequestAllowed(
            snapshot.policies().requestPolicies(),
            snapshot.statements(),
            snapshot.effectiveStatementKeys(),
            context.bypassStatements(),
            method,
            path,
            context.values()
        );
    }

    public ResponsePlan responsePlan(Authentication authentication, String method, String path) {
        Context context = this.context(authentication, method, path);
        AuthorizationSnapshot snapshot = this.authorizationSnapshot(context);
        return this.engine.planResponse(
            snapshot.policies().responsePolicies(),
            snapshot.statements(),
            snapshot.effectiveStatementKeys(),
            context.bypassStatements(),
            method,
            path,
            context.values()
        );
    }

    public void requireOrganization(Authentication authentication, UUID organizationId) {
        Context context = this.context(authentication, "ACL_MANAGEMENT", "/api/v0/acl-management");
        if (!hasSystemResourceManagement(authentication) && !organizationId.equals(context.organizationId())) {
            throw new AccessDeniedException(
                "Authorization resources can only be managed for the principal Organization"
            );
        }
    }

    private AuthorizationSnapshot authorizationSnapshot(Context context) {
        RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
        Object existing = attributes.getAttribute(AUTHORIZATION_SNAPSHOT_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (existing instanceof AuthorizationSnapshot snapshot) return snapshot;

        PolicySnapshot policySnapshot = this.policies.snapshot(context.organizationId());
        List<AclStatement> statements = this.access.statementCatalog(context.organizationId());
        Set<String> effective =
            context.userId() == null ? Set.of() : this.access.effectiveStatementKeys(context.userId());
        AuthorizationSnapshot snapshot = new AuthorizationSnapshot(policySnapshot, statements, effective);
        attributes.setAttribute(AUTHORIZATION_SNAPSHOT_ATTRIBUTE, snapshot, RequestAttributes.SCOPE_REQUEST);
        return snapshot;
    }

    private Context context(Authentication authentication, String method, String path) {
        Map<String, Object> values = new HashMap<>();
        values.put("principal.id", authentication.getName());
        values.put("principal.type", "unknown");
        values.put("request.method", method);
        values.put("request.path", path);
        UUID requestOrganizationId = organizationIdFromPath(path);
        if (requestOrganizationId != null) values.put("request.organizationId", requestOrganizationId);

        UUID organizationId = null;
        UUID userId = null;
        boolean bypassStatements = hasSystemResourceManagement(authentication);
        if (authentication instanceof JwtAuthenticationToken token) {
            String principalType = token.getToken().getClaimAsString("principal_type");
            if (principalType != null && !principalType.isBlank()) values.put("principal.type", principalType);
            String userIdClaim = token.getToken().getClaimAsString("user_id");
            if (userIdClaim != null && !userIdClaim.isBlank()) {
                userId = UUID.fromString(userIdClaim);
                UserService.UserInfo user = this.users.require(userId);
                values.put("principal.id", userId);
                values.put("principal.username", user.username());
                organizationId = user.organizationId();
                if (organizationId != null) values.put("principal.organizationId", organizationId);
                if (SystemUser.USERNAME.equals(user.username())) bypassStatements = true;
            }
        }
        return new Context(organizationId, userId, Map.copyOf(values), bypassStatements);
    }

    private static @Nullable UUID organizationIdFromPath(String path) {
        String[] segments = path.split("/");
        for (int index = 0; index + 1 < segments.length; index++) {
            if (!"organizations".equals(segments[index])) continue;
            try {
                return UUID.fromString(segments[index + 1]);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean hasSystemResourceManagement(Authentication authentication) {
        return authentication
            .getAuthorities()
            .stream()
            .anyMatch(authority -> SYSTEM_RESOURCES_MANAGE_AUTHORITY.equals(authority.getAuthority()));
    }

    private record Context(
        @Nullable UUID organizationId,
        @Nullable UUID userId,
        Map<String, Object> values,
        boolean bypassStatements
    ) {}

    private record AuthorizationSnapshot(
        PolicySnapshot policies,
        List<AclStatement> statements,
        Set<String> effectiveStatementKeys
    ) {}
}
