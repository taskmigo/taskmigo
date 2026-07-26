package com.taskmigo.console.access.internal.application.oauth.management;

public class OAuthClientNotFoundException extends RuntimeException {
  public OAuthClientNotFoundException(String clientId) {
    super("OAuth client not found: " + clientId);
  }
}
