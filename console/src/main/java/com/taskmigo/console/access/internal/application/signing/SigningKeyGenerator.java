package com.taskmigo.console.access.internal.application.signing;

import com.nimbusds.jose.jwk.RSAKey;

public interface SigningKeyGenerator {
  public RSAKey generate();
}
