package io.taskmigo.web.api.v0.resource;

import io.taskmigo.resource.ResourceException;
import io.taskmigo.web.api.v0.response.ApiResponse;
import io.taskmigo.web.api.v0.response.ApiResponseFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = ResourceController.class)
class ResourceExceptionHandler {

    private final ApiResponseFactory responses;

    ResourceExceptionHandler(ApiResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(ResourceException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> handleResourceException(ResourceException exception) {
        HttpStatus status = switch (exception.type()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        String exceptionMessage = exception.getMessage();
        String message = exceptionMessage == null ? "Resource operation failed" : exceptionMessage;
        String errorCode = switch (exception.type()) {
            case BAD_REQUEST -> "RESOURCE_BAD_REQUEST";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case CONFLICT -> "RESOURCE_CONFLICT";
        };
        String messageCode = switch (exception.type()) {
            case BAD_REQUEST -> "resource.bad_request";
            case NOT_FOUND -> "resource.not_found";
            case CONFLICT -> "resource.conflict";
        };
        return this.responses.failure(status, messageCode, message, new ApiResponse.Error(errorCode, message, null));
    }
}
