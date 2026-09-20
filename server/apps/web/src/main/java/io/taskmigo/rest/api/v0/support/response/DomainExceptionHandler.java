package io.taskmigo.rest.api.v0.support.response;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = RequestMapping.class)
final class DomainExceptionHandler {

    private final ApiResponseFactory responses;

    DomainExceptionHandler(ApiResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> domain(DomainException exception) {
        return this.failure(exception.type(), exception.getMessage());
    }

    private ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> failure(
        DomainFailureType type,
        @Nullable String exceptionMessage
    ) {
        TransportFailure mapping = switch (type) {
            case INVALID_INPUT -> new TransportFailure(HttpStatus.BAD_REQUEST, "BAD_REQUEST");
            case NOT_FOUND -> new TransportFailure(HttpStatus.NOT_FOUND, "NOT_FOUND");
            case CONFLICT -> new TransportFailure(HttpStatus.CONFLICT, "CONFLICT");
        };
        String message = exceptionMessage == null ? "Operation failed" : exceptionMessage;
        return this.responses.failure(
            mapping.status(),
            "domain." + mapping.legacyType().toLowerCase(),
            message,
            new ApiResponse.Error("DOMAIN_" + mapping.legacyType(), message, null)
        );
    }

    private record TransportFailure(HttpStatus status, String legacyType) {}
}
