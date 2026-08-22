package io.taskmigo.web.api;

import io.taskmigo.resource.ResourceException;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ResourceExceptionHandler {

    @ExceptionHandler(ResourceException.class)
    ResponseEntity<Map<String, String>> handleResourceException(ResourceException exception) {
        HttpStatus status = switch (exception.type()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        @Nullable
        String exceptionMessage = exception.getMessage();
        String message = exceptionMessage == null ? "Resource operation failed" : exceptionMessage;
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
