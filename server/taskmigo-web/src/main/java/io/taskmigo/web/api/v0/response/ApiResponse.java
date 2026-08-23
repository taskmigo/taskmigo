package io.taskmigo.web.api.v0.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@Schema(name = "ApiResponse", description = "Standard response envelope for every Taskmigo API v0 endpoint")
public record ApiResponse<T>(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean success,
    @JsonProperty("status_code") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int statusCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Message message,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable Error error,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Meta meta,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable T data
) {
    public record Message(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(
        @Nullable String code,
        @Nullable String message,
        @JsonProperty("form_errors") @Nullable Map<String, String> formErrors
    ) {}

    public record Meta(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Execution execution,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable Pagination pagination
    ) {}

    public record Execution(
        @JsonProperty("started_at")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
        Instant startedAt,
        @JsonProperty("duration_ms") @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long durationMs
    ) {}

    @Schema(oneOf = { OffsetPagination.class, CursorPagination.class })
    public sealed interface Pagination permits OffsetPagination, CursorPagination {
        String type();
    }

    public record OffsetPagination(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "offset") String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Offset offset
    ) implements Pagination {
        public OffsetPagination(Offset offset) {
            this("offset", offset);
        }
    }

    public record Offset(
        @JsonProperty("current_page")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        int currentPage,
        @JsonProperty("per_page") @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int perPage,
        @JsonProperty("total_items")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        long totalItems,
        @JsonProperty("total_pages") @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long totalPages
    ) {}

    public record CursorPagination(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "cursor") String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Cursor cursor
    ) implements Pagination {
        public CursorPagination(Cursor cursor) {
            this("cursor", cursor);
        }
    }

    public record Cursor(
        @JsonProperty("next_cursor")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @Nullable
        String nextCursor,
        @JsonProperty("prev_cursor")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @Nullable
        String prevCursor,
        @JsonProperty("has_more") @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasMore
    ) {}
}
