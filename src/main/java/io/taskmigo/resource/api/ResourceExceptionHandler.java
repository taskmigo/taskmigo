package io.taskmigo.resource.api;

import io.taskmigo.resource.ResourceException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ResourceExceptionHandler {
    @ExceptionHandler(ResourceException.class)
    ResponseEntity<Map<String, String>> handleResourceException(ResourceException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
