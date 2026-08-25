package io.taskmigo.web.api.v0.feature.group;

import io.taskmigo.group.GroupService;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v0")
class GroupController {

    private final GroupService groups;
    private final ApiResponseFactory responses;

    GroupController(GroupService groups, ApiResponseFactory responses) {
        this.groups = groups;
        this.responses = responses;
    }

    @PostMapping("/organizations/{organizationId}/groups")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(
        @PathVariable UUID organizationId,
        @Valid @RequestBody Request request
    ) {
        UUID id = this.groups.create(organizationId, request.name(), request.description());
        return this.responses.created(URI.create("/api/v0/groups/" + id), Map.of("id", id), "group.created", "Group created");
    }

    @PutMapping("/groups/{groupId}/members/{userId}")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> addMember(@PathVariable UUID groupId, @PathVariable UUID userId) {
        this.groups.addMember(groupId, userId);
        return this.responses.ok("group.member_added", "Group member added");
    }

    @DeleteMapping("/groups/{groupId}/members/{userId}")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> removeMember(@PathVariable UUID groupId, @PathVariable UUID userId) {
        this.groups.removeMember(groupId, userId);
        return this.responses.ok("group.member_removed", "Group member removed");
    }

    record Request(@NotBlank @Nullable String name, @Nullable String description) {}
}
