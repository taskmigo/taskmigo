package io.taskmigo.web.api.v0.feature.access;

import io.taskmigo.authorization.AuthorizationResource.Group;
import io.taskmigo.authorization.AuthorizationResource.Origin;
import io.taskmigo.authorization.AuthorizationResource.Role;
import io.taskmigo.authorization.AuthorizationResource.Statement;
import io.taskmigo.authorization.AuthorizationResourceService;
import io.taskmigo.user.UserService;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import io.taskmigo.web.security.ApiAclSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v0/organizations/{organizationId}/authorization")
class AuthorizationController {

    private final AuthorizationResourceService resources;
    private final UserService users;
    private final ApiAclSupport acl;
    private final ApiResponseFactory responses;

    AuthorizationController(
        AuthorizationResourceService resources,
        UserService users,
        ApiAclSupport acl,
        ApiResponseFactory responses
    ) {
        this.resources = resources;
        this.users = users;
        this.acl = acl;
        this.responses = responses;
    }

    @PutMapping("/statements/{key}")
    ResponseEntity<ApiResponse<Map<String, Object>, ApiResponse.BasicMeta>> putStatement(
        @PathVariable UUID organizationId,
        @PathVariable String key,
        @RequestBody Statement statement,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        requireKey(key, statement.key());
        UUID id = this.resources.upsertStatement(organizationId, statement, Origin.CUSTOM);
        return this.responses.ok(resource(id, key), "authorization.statement.saved", "Authorization Statement saved");
    }

    @PutMapping("/roles/{key}")
    ResponseEntity<ApiResponse<Map<String, Object>, ApiResponse.BasicMeta>> putRole(
        @PathVariable UUID organizationId,
        @PathVariable String key,
        @RequestBody Role role,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        requireKey(key, role.key());
        UUID id = this.resources.upsertRole(organizationId, role, Origin.CUSTOM);
        return this.responses.ok(resource(id, key), "authorization.role.saved", "Authorization Role saved");
    }

    @PutMapping("/groups/{key}")
    ResponseEntity<ApiResponse<Map<String, Object>, ApiResponse.BasicMeta>> putGroup(
        @PathVariable UUID organizationId,
        @PathVariable String key,
        @RequestBody Group group,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        requireKey(key, group.key());
        UUID id = this.resources.upsertGroup(organizationId, group, Origin.CUSTOM);
        return this.responses.ok(resource(id, key), "authorization.group.saved", "Authorization Group saved");
    }

    @PutMapping("/users/{userId}/statements/{key}")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> assignStatement(
        @PathVariable UUID organizationId,
        @PathVariable UUID userId,
        @PathVariable String key,
        Authentication authentication
    ) {
        requireAssignableUser(authentication, organizationId, userId);
        this.resources.assignStatement(userId, key);
        return this.responses.ok("authorization.statement.assigned", "Authorization Statement assigned");
    }

    @PutMapping("/users/{userId}/roles/{key}")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> assignRole(
        @PathVariable UUID organizationId,
        @PathVariable UUID userId,
        @PathVariable String key,
        Authentication authentication
    ) {
        requireAssignableUser(authentication, organizationId, userId);
        this.resources.assignRole(userId, key);
        return this.responses.ok("authorization.role.assigned", "Authorization Role assigned");
    }

    @PutMapping("/users/{userId}/groups/{key}")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> assignGroup(
        @PathVariable UUID organizationId,
        @PathVariable UUID userId,
        @PathVariable String key,
        Authentication authentication
    ) {
        requireAssignableUser(authentication, organizationId, userId);
        this.resources.assignGroup(userId, key);
        return this.responses.ok("authorization.group.assigned", "Authorization Group assigned");
    }

    private void requireAssignableUser(Authentication authentication, UUID organizationId, UUID userId) {
        this.acl.requireOrganization(authentication, organizationId);
        UserService.UserInfo user = this.users.require(userId);
        if (!organizationId.equals(user.organizationId())) throw new AccessDeniedException(
            "Authorization assignments cannot cross organization boundaries"
        );
    }

    private static void requireKey(String pathKey, String bodyKey) {
        if (!pathKey.equals(bodyKey)) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Authorization resource key must match the URL key"
        );
    }

    private static Map<String, Object> resource(UUID id, String key) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("key", key);
        result.put("origin", "custom");
        return Map.copyOf(result);
    }
}
