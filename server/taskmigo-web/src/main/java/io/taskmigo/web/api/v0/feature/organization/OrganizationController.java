package io.taskmigo.web.api.v0.feature.organization;

import io.taskmigo.organization.OrganizationService;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v0/organizations")
class OrganizationController {

    private final OrganizationService organizations;
    private final ApiResponseFactory responses;

    OrganizationController(OrganizationService organizations, ApiResponseFactory responses) {
        this.organizations = organizations;
        this.responses = responses;
    }

    @PostMapping
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(@Valid @RequestBody Request request) {
        UUID id = this.organizations.create(request.key(), request.name());
        return this.responses.created(URI.create("/api/v0/organizations/" + id), Map.of("id", id), "organization.created", "Organization created");
    }

    record Request(@NotBlank @Nullable String key, @NotBlank @Nullable String name) {}
}
