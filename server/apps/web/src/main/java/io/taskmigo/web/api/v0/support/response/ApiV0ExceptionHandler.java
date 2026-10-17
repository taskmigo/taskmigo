package io.taskmigo.web.api.v0.support.response;

import io.taskmigo.query.FilterByException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(annotations = RequestMapping.class)
final class ApiV0ExceptionHandler {

    private final ApiResponseFactory responses;

    ApiV0ExceptionHandler(ApiResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
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
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> handleUnreadableRequest() {
        String message = "Request body is malformed or unreadable";
        return this.responses.failure(
            HttpStatus.BAD_REQUEST,
            "request.malformed",
            message,
            new ApiResponse.Error("MALFORMED_REQUEST", message, null)
        );
    }

    @ExceptionHandler(FilterByException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> handleFilterBy(FilterByException exception) {
        String message = exception.getMessage() == null ? "filterBy is invalid" : exception.getMessage();
        return this.responses.failure(
            HttpStatus.BAD_REQUEST,
            "query.filter.invalid",
            message,
            new ApiResponse.Error("INVALID_FILTER", message, null)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> handleIllegalArgument(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Request is invalid" : exception.getMessage();
        if (isMissingResource(message)) {
            return this.responses.failure(
                HttpStatus.NOT_FOUND,
                "resource.not-found",
                message,
                new ApiResponse.Error("NOT_FOUND", message, null)
            );
        }
        if (isTicketClientError(message)) {
            return this.responses.failure(
                HttpStatus.BAD_REQUEST,
                "request.invalid",
                message,
                new ApiResponse.Error("INVALID_REQUEST", message, null)
            );
        }
        return this.internalError();
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> handleIllegalState(IllegalStateException exception) {
        String message =
            exception.getMessage() == null ? "Resource state conflicts with the request" : exception.getMessage();
        if (!isTicketStateConflict(message)) {
            return this.internalError();
        }
        return this.responses.failure(
            HttpStatus.CONFLICT,
            "resource.state.conflict",
            message,
            new ApiResponse.Error("STATE_CONFLICT", message, null)
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> handleAccessDenied() {
        String message = "Access is denied";
        return this.responses.failure(
            HttpStatus.FORBIDDEN,
            "security.forbidden",
            message,
            new ApiResponse.Error("FORBIDDEN", message, null)
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> handleUnexpected() {
        return this.internalError();
    }

    private ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> internalError() {
        String message = "An unexpected error occurred";
        return this.responses.failure(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal.error",
            message,
            new ApiResponse.Error("INTERNAL_ERROR", message, null)
        );
    }

    private static boolean isMissingResource(String message) {
        return (
            message.startsWith("Unknown or unauthorized Ticket") ||
            message.startsWith("Unknown Ticket Type") ||
            message.startsWith("Unknown EAV schema version")
        );
    }

    private static boolean isTicketClientError(String message) {
        return (
            message.startsWith("Ticket ") ||
            message.startsWith("EAV ") ||
            message.startsWith("Unknown EAV field key") ||
            message.startsWith("Missing required EAV field") ||
            message.startsWith("Value for EAV field") ||
            message.startsWith("Published EAV field identity") ||
            message.startsWith("Custom Ticket field key") ||
            message.startsWith("Query field") ||
            message.startsWith("Persistence ") ||
            message.startsWith("Predicate value")
        );
    }

    private static boolean isTicketStateConflict(String message) {
        return (
            message.startsWith("Ticket Type") ||
            message.startsWith("Ticket schema instance") ||
            message.startsWith("EAV instance") ||
            message.startsWith("New EAV instances") ||
            message.startsWith("Only a draft schema version") ||
            message.startsWith("Only a published schema version")
        );
    }
}
