package io.taskmigo.web.api.v0.resource;

import io.taskmigo.resource.ResourceException;
import io.taskmigo.web.api.v0.response.ApiResponse;
import io.taskmigo.web.api.v0.response.ApiResponseFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.taskmigo.web.api.v0")
class ResourceExceptionHandler {

    private final ApiResponseFactory responses;

    ResourceExceptionHandler(ApiResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(ResourceException.class)
    ResponseEntity<ApiResponse<Void>> handleResourceException(ResourceException exception) {
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
        return this.responses.failure(
                status,
                messageCode,
                message,
                new ApiResponse.Error(errorCode, message, null)
            );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> formErrors = new LinkedHashMap<>();
        exception
            .getBindingResult()
            .getFieldErrors()
            .forEach(error -> {
                String defaultMessage = error.getDefaultMessage();
                formErrors.putIfAbsent(error.getField(), defaultMessage == null ? "is invalid" : defaultMessage);
            });
        String message = "Request validation failed";
        return this.responses.failure(
                HttpStatus.valueOf(422),
                "validation.failed",
                message,
                new ApiResponse.Error("VALIDATION_ERROR", message, formErrors)
            );
    }
}
