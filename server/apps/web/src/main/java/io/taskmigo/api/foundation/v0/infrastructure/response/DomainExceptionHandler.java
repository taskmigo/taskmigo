package io.taskmigo.api.foundation.v0.infrastructure.response;

import io.taskmigo.foundation.DomainException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RestControllerAdvice(annotations = RequestMapping.class)
final class DomainExceptionHandler {

    private final ApiResponseFactory responses;

    DomainExceptionHandler(ApiResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> domain(DomainException exception) {
        return this.failure(exception.type().name(), exception.getMessage());
    }

    private ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> failure(
        String type,
        @Nullable String exceptionMessage
    ) {
        HttpStatus status = switch (type) {
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        String message = exceptionMessage == null ? "Operation failed" : exceptionMessage;
        String code = "DOMAIN_" + type;
        return this.responses.failure(
            status,
            "domain." + type.toLowerCase(),
            message,
            new ApiResponse.Error(code, message, null)
        );
    }
}
