package com.taskmigo.console.access.internal.application.oauth.management;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SecureDeletionTokenGenerator implements DeletionTokenGenerator {
  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public String generate() {
    byte[] value = new byte[32];
    this.secureRandom.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }
}
