package io.taskmigo.web.api.v0.feature.user;

import io.taskmigo.user.UserService;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v0/users")
class UserController {

    private final UserService users;
    private final ApiResponseFactory responses;

    UserController(UserService users, ApiResponseFactory responses) {
        this.users = users;
        this.responses = responses;
    }

    @PostMapping
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(@Valid @RequestBody Request request) {
        UUID id = this.users.create(
            request.organizationId(),
            request.username(),
            request.emails(),
            request.firstName(),
            request.lastName()
        );
        return this.responses.created(
            URI.create("/api/v0/users/" + id),
            Map.of("id", id),
            "resource.user.created",
            "User created"
        );
    }

    record Request(
        @Nullable UUID organizationId,
        @NotBlank @Nullable String username,
        @Nullable Set<@Email @NotBlank String> emails,
        @NotBlank @Nullable String firstName,
        @NotBlank @Nullable String lastName
    ) {}
}
