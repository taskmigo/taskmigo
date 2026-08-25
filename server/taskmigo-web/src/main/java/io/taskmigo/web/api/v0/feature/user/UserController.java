package io.taskmigo.web.api.v0.feature.user;

import io.taskmigo.user.UserService;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v0/organizations/{organizationId}/users")
class UserController {

    private final UserService users;
    private final ApiResponseFactory responses;

    UserController(UserService users, ApiResponseFactory responses) {
        this.users = users;
        this.responses = responses;
    }

    @PostMapping
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(
        @PathVariable UUID organizationId,
        @Valid @RequestBody Request request
    ) {
        UUID id = this.users.create(organizationId, request.username(), request.email(), request.displayName());
        return this.responses.created(
            URI.create("/api/v0/users/" + id),
            Map.of("id", id),
            "resource.user.created",
            "User created"
        );
    }

    record Request(
        @NotBlank @Nullable String username,
        @Email @NotBlank @Nullable String email,
        @NotBlank @Nullable String displayName
    ) {}
}
