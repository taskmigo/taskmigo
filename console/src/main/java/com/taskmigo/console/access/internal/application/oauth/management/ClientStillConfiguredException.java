package com.taskmigo.console.access.internal.application.oauth.management;

public class ClientStillConfiguredException extends RuntimeException {
  public ClientStillConfiguredException(String clientId) {
    super("OAuth client is still configured: " + clientId);
  }
}
