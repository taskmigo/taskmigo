package io.taskmigo.web.api.v0.response;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public final class ApiResponseFactory {

    private final HttpServletRequest request;

    public ApiResponseFactory(HttpServletRequest request) {
        this.request = request;
    }

    public <T> ResponseEntity<ApiResponse<T>> ok(T data, String messageCode, String messageText) {
        return ResponseEntity.ok(this.success(HttpStatus.OK, data, messageCode, messageText));
    }

    public ResponseEntity<ApiResponse<Void>> ok(String messageCode, String messageText) {
        return ResponseEntity.ok(this.success(HttpStatus.OK, null, messageCode, messageText));
    }

    public <T> ResponseEntity<ApiResponse<T>> created(
        URI location,
        T data,
        String messageCode,
        String messageText
    ) {
        return ResponseEntity.created(location).body(this.success(HttpStatus.CREATED, data, messageCode, messageText));
    }

    public ResponseEntity<ApiResponse<Void>> failure(
        HttpStatus status,
        String messageCode,
        String messageText,
        ApiResponse.Error error
    ) {
        return ResponseEntity.status(status)
            .body(new ApiResponse<>(false, status.value(), new ApiResponse.Message(messageCode, messageText), error, this.meta(), null));
    }

    private <T> ApiResponse<T> success(HttpStatus status, T data, String messageCode, String messageText) {
        return new ApiResponse<>(
            true,
            status.value(),
            new ApiResponse.Message(messageCode, messageText),
            null,
            this.meta(),
            data
        );
    }

    private ApiResponse.Meta meta() {
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

        return new ApiResponse.Meta(new ApiResponse.Execution(startedAt, durationMs), null);
    }
}
