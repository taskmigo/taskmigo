package io.taskmigo.web.api.v0.infrastructure.response;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "io.taskmigo.web.api.v0")
final class ApiV0ExceptionHandler {

    private final ApiResponseFactory responses;

    ApiV0ExceptionHandler(ApiResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> handleValidation(
        MethodArgumentNotValidException exception
    ) {
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
            HttpStatus.UNPROCESSABLE_CONTENT,
            "validation.failed",
            message,
            new ApiResponse.Error("VALIDATION_ERROR", message, formErrors)
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> handleUnreadableRequest(
        HttpMessageNotReadableException exception
    ) {
        String message = "Request body is malformed or unreadable";
        return this.responses.failure(
            HttpStatus.BAD_REQUEST,
            "request.malformed",
            message,
            new ApiResponse.Error("MALFORMED_REQUEST", message, null)
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> handleAccessDenied(AccessDeniedException exception) {
        String message = "Access is denied";
        return this.responses.failure(
            HttpStatus.FORBIDDEN,
            "security.forbidden",
            message,
            new ApiResponse.Error("FORBIDDEN", message, null)
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> handleUnexpected(Exception exception) {
        String message = "An unexpected error occurred";
        return this.responses.failure(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal.error",
            message,
            new ApiResponse.Error("INTERNAL_ERROR", message, null)
        );
    }
}
