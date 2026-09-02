package io.taskmigo.api.v0.infrastructure.response;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/// Builds API v0 response envelopes and attaches execution metadata captured for the current HTTP request.
///
/// When timing attributes are unavailable, execution metadata falls back to the current instant and a zero duration.
@Component
public final class ApiResponseFactory {

    private final HttpServletRequest request;

    public ApiResponseFactory(HttpServletRequest request) {
        this.request = request;
    }

    public <T> ResponseEntity<ApiResponse<T, ApiResponse.BasicMeta>> ok(
        T data,
        String messageCode,
        String messageText
    ) {
        return ResponseEntity.ok(this.success(HttpStatus.OK, data, this.basicMeta(), messageCode, messageText));
    }

    public <T> ResponseEntity<ApiResponse<T, ApiResponse.OffsetMeta>> ok(
        T data,
        ApiResponse.OffsetPagination pagination,
        String messageCode,
        String messageText
    ) {
        return ResponseEntity.ok(
            this.success(
                HttpStatus.OK,
                data,
                new ApiResponse.OffsetMeta(this.execution(), pagination),
                messageCode,
                messageText
            )
        );
    }

    public <T> ResponseEntity<ApiResponse<T, ApiResponse.CursorMeta>> ok(
        T data,
        ApiResponse.CursorPagination pagination,
        String messageCode,
        String messageText
    ) {
        return ResponseEntity.ok(
            this.success(
                HttpStatus.OK,
                data,
                new ApiResponse.CursorMeta(this.execution(), pagination),
                messageCode,
                messageText
            )
        );
    }

    public ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> ok(String messageCode, String messageText) {
        return ResponseEntity.ok(this.success(HttpStatus.OK, null, this.basicMeta(), messageCode, messageText));
    }

    public <T> ResponseEntity<ApiResponse<T, ApiResponse.BasicMeta>> created(
        URI location,
        T data,
        String messageCode,
        String messageText
    ) {
        return ResponseEntity.created(location).body(
            this.success(HttpStatus.CREATED, data, this.basicMeta(), messageCode, messageText)
        );
    }

    public ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> failure(
        HttpStatus status,
        String messageCode,
        String messageText,
        ApiResponse.Error error
    ) {
        return ResponseEntity.status(status).body(this.failureBody(status, messageCode, messageText, error));
    }

    /// Creates a failure envelope without coupling the caller to a [ResponseEntity].
    ///
    /// @param status the HTTP status represented by the envelope
    /// @param messageCode the stable machine-readable message code
    /// @param messageText the human-readable message text
    /// @param error the structured error details
    /// @return a failure envelope with basic execution metadata and no data payload
    public ApiResponse<Void, ApiResponse.BasicMeta> failureBody(
        HttpStatus status,
        String messageCode,
        String messageText,
        ApiResponse.Error error
    ) {
        return new ApiResponse<>(
            false,
            status.value(),
            new ApiResponse.Message(messageCode, messageText),
            error,
            this.basicMeta(),
            null
        );
    }

    private <T, M extends ApiResponse.Meta> ApiResponse<T, M> success(
        HttpStatus status,
        @Nullable T data,
        M meta,
        String messageCode,
        String messageText
    ) {
        return new ApiResponse<>(
            true,
            status.value(),
            new ApiResponse.Message(messageCode, messageText),
            null,
            meta,
            data
        );
    }

    private ApiResponse.BasicMeta basicMeta() {
        return new ApiResponse.BasicMeta(this.execution());
    }

    private ApiResponse.Execution execution() {
        var startedAtAttribute = this.request.getAttribute(ApiExecutionTimingFilter.STARTED_AT_ATTRIBUTE);
        var startedAt = startedAtAttribute instanceof Instant value ? value : Instant.now();
        var startNanosAttribute = this.request.getAttribute(ApiExecutionTimingFilter.START_NANOS_ATTRIBUTE);
        var duration =
            startNanosAttribute instanceof Long value
                ? TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - value))
                : 0L;
        return new ApiResponse.Execution(startedAt, duration);
    }
}
