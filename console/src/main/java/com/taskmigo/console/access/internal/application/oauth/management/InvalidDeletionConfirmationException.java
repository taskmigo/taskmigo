package com.taskmigo.console.access.internal.application.oauth.management;

public class InvalidDeletionConfirmationException extends RuntimeException {
  public InvalidDeletionConfirmationException() {
    super("Deletion confirmation is invalid or expired");
  }
}
