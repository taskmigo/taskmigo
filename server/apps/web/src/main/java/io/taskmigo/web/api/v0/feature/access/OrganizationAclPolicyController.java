package io.taskmigo.web.api.v0.feature.access;

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

@RestController
@RequestMapping("/api/v0/organizations/{organizationId}/acl-policies")
class OrganizationAclPolicyController {

    private final OrganizationAclPolicyService policies;
    private final ApiAclSupport acl;
    private final ApiResponseFactory responses;

    OrganizationAclPolicyController(
        OrganizationAclPolicyService policies,
        ApiAclSupport acl,
        ApiResponseFactory responses
    ) {
        this.policies = policies;
        this.acl = acl;
        this.responses = responses;
    }

    @PutMapping("/{name}")
    ResponseEntity<ApiResponse<Map<String, Object>, ApiResponse.BasicMeta>> put(
        @PathVariable UUID organizationId,
        @PathVariable String name,
        @RequestBody PolicyRequest request,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        this.policies.upsert(
            organizationId,
            name,
            new OrganizationAclPolicyService.Policy(request.kind(), request.spec())
        );
        return this.responses.ok(Map.of("name", name, "kind", request.kind()), "acl.policy.saved", "ACL policy saved");
    }

    @GetMapping
    ResponseEntity<ApiResponse<List<String>, ApiResponse.BasicMeta>> list(
        @PathVariable UUID organizationId,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        return this.responses.ok(this.policies.list(organizationId), "acl.policy.listed", "ACL policies listed");
    }

    @DeleteMapping("/{name}")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> delete(
        @PathVariable UUID organizationId,
        @PathVariable String name,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        this.policies.delete(organizationId, name);
        return this.responses.ok("acl.policy.deleted", "ACL policy deleted");
    }

    record PolicyRequest(String kind, Map<String, Object> spec) {}
}
