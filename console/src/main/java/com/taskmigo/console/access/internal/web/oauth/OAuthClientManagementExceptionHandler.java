package com.taskmigo.console.access.internal.web.oauth;

import com.taskmigo.console.access.internal.application.oauth.management.ClientStillConfiguredException;
import com.taskmigo.console.access.internal.application.oauth.management.InvalidDeletionConfirmationException;
import com.taskmigo.console.access.internal.application.oauth.management.OAuthClientNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {OAuthClientManagementController.class})
public class OAuthClientManagementExceptionHandler {
  @ExceptionHandler(value = {OAuthClientNotFoundException.class})
  ProblemDetail notFound(OAuthClientNotFoundException exception) {
    return OAuthClientManagementExceptionHandler.problem(
        HttpStatus.NOT_FOUND, "oauth_client_not_found", exception.getMessage());
  }

  @ExceptionHandler(value = {ClientStillConfiguredException.class})
  ProblemDetail stillConfigured(ClientStillConfiguredException exception) {
    return OAuthClientManagementExceptionHandler.problem(
        HttpStatus.CONFLICT, "client_still_configured", exception.getMessage());
  }

  @ExceptionHandler(value = {InvalidDeletionConfirmationException.class})
  ProblemDetail invalidConfirmation(InvalidDeletionConfirmationException exception) {
    return OAuthClientManagementExceptionHandler.problem(
        HttpStatus.CONFLICT, "invalid_deletion_confirmation", exception.getMessage());
  }

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    return problem;
  }
}
