package io.taskmigo.web.api.v0.feature;

import io.taskmigo.access.AccessException;
import io.taskmigo.group.GroupException;
import io.taskmigo.organization.OrganizationException;
import io.taskmigo.project.ProjectException;
import io.taskmigo.user.UserException;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "io.taskmigo.web.api.v0.feature")
class DomainExceptionHandler {

    private final ApiResponseFactory responses;

    DomainExceptionHandler(ApiResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(OrganizationException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> organization(OrganizationException exception) {
        return this.failure(exception.type().name(), exception.getMessage());
    }

    @ExceptionHandler(UserException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> user(UserException exception) {
        return this.failure(exception.type().name(), exception.getMessage());
    }

    @ExceptionHandler(GroupException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> group(GroupException exception) {
        return this.failure(exception.type().name(), exception.getMessage());
    }

    @ExceptionHandler(AccessException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> access(AccessException exception) {
        return this.failure(exception.type().name(), exception.getMessage());
    }

    @ExceptionHandler(ProjectException.class)
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> project(ProjectException exception) {
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
