package io.taskmigo.web.api.v0.feature.access;

import io.taskmigo.access.AccessService;
import io.taskmigo.access.PermissionCatalog;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v0")
class AccessController {

    private final AccessService access;
    private final ApiResponseFactory responses;

    AccessController(AccessService access, ApiResponseFactory responses) {
        this.access = access;
        this.responses = responses;
    }

    @GetMapping("/permissions")
    ResponseEntity<ApiResponse<Set<String>, ApiResponse.BasicMeta>> permissions() {
        return this.responses.ok(PermissionCatalog.ALL, "resource.permissions.retrieved", "Permissions retrieved");
    }

    @PostMapping("/organizations/{organizationId}/roles")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> createRole(
        @PathVariable UUID organizationId,
        @Valid @RequestBody Request request
    ) {
        UUID id = this.access.createRole(organizationId, request.name(), request.description(), request.permissions());
        return this.responses.created(
            URI.create("/api/v0/roles/" + id),
            Map.of("id", id),
            "resource.role.created",
            "Role created"
        );
    }

    record Request(@NotBlank @Nullable String name, @Nullable String description, @Nullable Set<String> permissions) {}
}
