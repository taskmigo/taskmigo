package com.taskmigo.console.access.internal.application.signing;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;

public interface KeyPairGeneratorFactory {
  public KeyPairGenerator create() throws GeneralSecurityException;
}
