package com.taskmigo.console.access.internal.application.signing;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import org.springframework.stereotype.Component;

@Component
public class DefaultKeyPairGeneratorFactory implements KeyPairGeneratorFactory {
  @Override
  public KeyPairGenerator create() throws GeneralSecurityException {
    return KeyPairGenerator.getInstance("RSA");
  }
}
