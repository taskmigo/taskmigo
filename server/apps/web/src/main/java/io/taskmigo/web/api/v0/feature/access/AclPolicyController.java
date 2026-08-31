package io.taskmigo.web.api.v0.feature.access;

import io.taskmigo.acl.AclPolicyRegistry;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import io.taskmigo.web.security.ApiAclSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// POC management API for organization-scoped custom ACL policies. System policies are intentionally absent here.
@RestController
@RequestMapping("/api/v0/organizations/{organizationId}/acl-policies")
class AclPolicyController {

    private final AclPolicyRegistry policies;
    private final ApiResponseFactory responses;
    private final ApiAclSupport acl;

    AclPolicyController(AclPolicyRegistry policies, ApiResponseFactory responses, ApiAclSupport acl) {
        this.policies = policies;
        this.responses = responses;
        this.acl = acl;
    }

    @PutMapping("/{name}")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> put(
        @PathVariable UUID organizationId,
        @PathVariable String name,
        @RequestBody Map<String, Object> definition,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        this.policies.upsertCustom(organizationId, name, definition);
        return this.responses.ok("acl.policy.saved", "ACL policy saved");
    }

    @GetMapping
    ResponseEntity<ApiResponse<List<String>, ApiResponse.BasicMeta>> list(
        @PathVariable UUID organizationId,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        return this.responses.ok(
            this.policies.customPolicyNames(organizationId),
            "acl.policy.listed",
            "ACL policies listed"
        );
    }

    @DeleteMapping("/{name}")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> delete(
        @PathVariable UUID organizationId,
        @PathVariable String name,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        this.policies.deleteCustom(organizationId, name);
        return this.responses.ok("acl.policy.deleted", "ACL policy deleted");
    }
}
