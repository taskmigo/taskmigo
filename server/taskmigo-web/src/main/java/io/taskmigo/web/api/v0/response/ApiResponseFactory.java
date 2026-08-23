package io.taskmigo.web.api.v0.response;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

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
            this.success(HttpStatus.OK, data, new ApiResponse.OffsetMeta(this.execution(), pagination), messageCode, messageText)
        );
    }

    public <T> ResponseEntity<ApiResponse<T, ApiResponse.CursorMeta>> ok(
        T data,
        ApiResponse.CursorPagination pagination,
        String messageCode,
        String messageText
    ) {
        return ResponseEntity.ok(
            this.success(HttpStatus.OK, data, new ApiResponse.CursorMeta(this.execution(), pagination), messageCode, messageText)
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
        Instant startedAt = Instant.now();
        long durationMs = 0;

        Object startedAtAttribute = this.request.getAttribute(ApiExecutionTimingFilter.STARTED_AT_ATTRIBUTE);
        if (startedAtAttribute instanceof Instant value) {
            startedAt = value;
        }

        Object startNanosAttribute = this.request.getAttribute(ApiExecutionTimingFilter.START_NANOS_ATTRIBUTE);
        if (startNanosAttribute instanceof Long value) {
            durationMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - value));
        }

        return new ApiResponse.Execution(startedAt, durationMs);
    }
}
