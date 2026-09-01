package io.taskmigo.web.api.v0.infrastructure.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.web.api.v0.infrastructure.pagination.OffsetPageRequest;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@Schema
public record ApiResponse<T, M extends ApiResponse.Meta>(
    @Schema(description = "Whether the request completed successfully", requiredMode = Schema.RequiredMode.REQUIRED) boolean success,
    @Schema(description = "HTTP status code of the response", requiredMode = Schema.RequiredMode.REQUIRED) int statusCode,
    @Schema(description = "Machine-readable and human-readable outcome message", requiredMode = Schema.RequiredMode.REQUIRED) Message message,
    @Schema(description = "Error details when the request fails", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable Error error,
    @Schema(description = "Execution and pagination metadata", requiredMode = Schema.RequiredMode.REQUIRED) M meta,
    @Schema(description = "Response payload", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) @Nullable T data
) {
    public record Message(
        @Schema(description = "Stable code for the response outcome", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(description = "Human-readable description of the response outcome", requiredMode = Schema.RequiredMode.REQUIRED) String text
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(
        @Schema(description = "Stable code identifying the error", nullable = true) @Nullable String code,
        @Schema(description = "Human-readable description of the error", nullable = true) @Nullable String message,
        @Schema(description = "Validation errors keyed by request field name", nullable = true) @Nullable Map<String, String> formErrors
    ) {}

    public sealed interface Meta permits BasicMeta, OffsetMeta, CursorMeta {
        Execution execution();
    }

    @Schema(description = "Response metadata for an operation that does not paginate its data")
    public record BasicMeta(
        @Schema(description = "Timing information for processing the request", requiredMode = Schema.RequiredMode.REQUIRED) Execution execution
    ) implements Meta {}

    @Schema(description = "Response metadata for an operation using offset pagination")
    public record OffsetMeta(
        @Schema(description = "Timing information for processing the request", requiredMode = Schema.RequiredMode.REQUIRED) Execution execution,
        @Schema(description = "Offset pagination state for this response", requiredMode = Schema.RequiredMode.REQUIRED) OffsetPagination pagination
    ) implements Meta {}

    @Schema(description = "Response metadata for an operation using cursor pagination")
    public record CursorMeta(
        @Schema(description = "Timing information for processing the request", requiredMode = Schema.RequiredMode.REQUIRED) Execution execution,
        @Schema(description = "Cursor pagination state for this response", requiredMode = Schema.RequiredMode.REQUIRED) CursorPagination pagination
    ) implements Meta {}

    public record Execution(
        @Schema(description = "Time at which server processing began", requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") Instant startedAt,
        @Schema(description = "Server processing time in milliseconds", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long duration
    ) {}

    @Schema(description = "Offset pagination metadata")
    public record OffsetPagination(
        @Schema(description = "Pagination strategy", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "offset") String type,
        @Schema(description = "Offset pagination values", requiredMode = Schema.RequiredMode.REQUIRED) Offset offset
    ) {
        public OffsetPagination(Offset offset) {
            this("offset", offset);
        }

        /// Creates offset metadata from the request parameters and the corresponding page result.
        ///
        /// @param request the validated pagination request
        /// @param page the page result returned by the application service
        public OffsetPagination(OffsetPageRequest request, OffsetPage<?> page) {
            this(new Offset(request.page(), request.pageSize(), page.totalItems(), page.totalPages()));
        }
    }

    public record Offset(
        @Schema(description = "One-based index of the current page", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int currentPage,
        @Schema(description = "Maximum number of items in each page", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int pageSize,
        @Schema(description = "Total number of items matching the request", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long totalItems,
        @Schema(description = "Total number of available pages", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long totalPages
    ) {}

    @Schema(description = "Cursor pagination metadata")
    public record CursorPagination(
        @Schema(description = "Pagination strategy", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "cursor") String type,
        @Schema(description = "Cursor pagination values", requiredMode = Schema.RequiredMode.REQUIRED) Cursor cursor
    ) {
        public CursorPagination(Cursor cursor) {
            this("cursor", cursor);
        }
    }

    public record Cursor(
        @Schema(description = "Cursor to retrieve the next page, when one exists", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @Nullable
        String nextCursor,
        @Schema(description = "Cursor to retrieve the previous page, when one exists", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        @Nullable
        String prevCursor,
        @Schema(description = "Whether another page is available after this one", requiredMode = Schema.RequiredMode.REQUIRED) boolean hasMore
    ) {}
}
