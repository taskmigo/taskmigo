package com.taskmigo.console.access.internal.application.signing;

import com.nimbusds.jose.jwk.RSAKey;
import com.taskmigo.console.access.internal.domain.signing.SigningKey;
import com.taskmigo.console.access.internal.persistence.signing.SigningKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class SigningKeyLifecycle {
  private final SigningKeyRepository signingKeys;
  private final SigningKeyGenerator generator;

  public SigningKeyLifecycle(SigningKeyRepository signingKeys, SigningKeyGenerator generator) {
    this.signingKeys = signingKeys;
    this.generator = generator;
  }

  public void ensureActiveKey() {
    if (this.signingKeys.existsByActiveTrue()) {
      return;
    }
    RSAKey key = this.generator.generate();
    try {
      this.signingKeys.saveAndFlush(new SigningKey(key.getKeyID(), key.toJSONString()));
    } catch (DataIntegrityViolationException dataIntegrityViolationException) {
      // empty catch block
    }
  }
}
