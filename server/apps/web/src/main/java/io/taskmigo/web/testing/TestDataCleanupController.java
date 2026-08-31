package io.taskmigo.web.testing;

import io.swagger.v3.oas.annotations.Hidden;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/v0/testing")
@ConditionalOnProperty(prefix = "taskmigo.testing", name = "cleanup-enabled", havingValue = "true")
class TestDataCleanupController {

    private final TestDataCleanupService cleaner;
    private final ApiResponseFactory responses;

    TestDataCleanupController(TestDataCleanupService cleaner, ApiResponseFactory responses) {
        this.cleaner = cleaner;
        this.responses = responses;
    }

    @PostMapping("/cleanup")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> cleanup(@RequestBody Request request) {
        this.cleaner.cleanup(
            new TestDataCleanupService.Resources(
                orEmpty(request.organizations()),
                orEmpty(request.users()),
                orEmpty(request.roles()),
                orEmpty(request.groups()),
                orEmpty(request.projects())
            )
        );
        return this.responses.ok("testing.data.cleaned", "Test data cleaned");
    }

    private static <T> Set<T> orEmpty(@Nullable Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    record Request(
        @Nullable Set<UUID> organizations,
        @Nullable Set<UUID> users,
        @Nullable Set<UUID> roles,
        @Nullable Set<UUID> groups,
        @Nullable Set<UUID> projects
    ) {}
}
