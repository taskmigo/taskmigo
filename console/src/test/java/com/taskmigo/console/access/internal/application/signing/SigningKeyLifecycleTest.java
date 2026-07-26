package com.taskmigo.console.access.internal.application.signing;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.taskmigo.console.access.internal.domain.signing.SigningKey;
import com.taskmigo.console.access.internal.persistence.signing.SigningKeyRepository;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;
import org.springframework.dao.DataIntegrityViolationException;

class SigningKeyLifecycleTest {
  private final SigningKeyRepository repository =
      (SigningKeyRepository) Mockito.mock(SigningKeyRepository.class);
  private final SigningKeyGenerator generator =
      (SigningKeyGenerator) Mockito.mock(SigningKeyGenerator.class);
  private final SigningKeyLifecycle lifecycle =
      new SigningKeyLifecycle(this.repository, this.generator);

  SigningKeyLifecycleTest() {}

  @Test
  void keepsExistingActiveKey() {
    Mockito.when(this.repository.existsByActiveTrue()).thenReturn(true);
    this.lifecycle.ensureActiveKey();
    ((SigningKeyGenerator) Mockito.verify(this.generator, (VerificationMode) Mockito.never()))
        .generate();
    ((SigningKeyRepository) Mockito.verify(this.repository, (VerificationMode) Mockito.never()))
        .saveAndFlush(((SigningKey) ArgumentMatchers.any()));
  }

  @Test
  void createsAndPersistsMissingKey() {
    Mockito.when(this.generator.generate()).thenReturn(SigningKeyLifecycleTest.key());
    this.lifecycle.ensureActiveKey();
    ((SigningKeyRepository) Mockito.verify(this.repository))
        .saveAndFlush(((SigningKey) ArgumentMatchers.any(SigningKey.class)));
  }

  @Test
  void acceptsConcurrentReplicaWinningTheInsert() {
    Mockito.when(this.generator.generate()).thenReturn(SigningKeyLifecycleTest.key());
    Mockito.when(this.repository.saveAndFlush(((SigningKey) ArgumentMatchers.any())))
        .thenThrow(
            new Throwable[] {new DataIntegrityViolationException("active key already exists")});
    this.lifecycle.ensureActiveKey();
    ((SigningKeyRepository) Mockito.verify(this.repository))
        .saveAndFlush(((SigningKey) ArgumentMatchers.any(SigningKey.class)));
  }

  private static RSAKey key() {
    return new RSAKey.Builder(
            Base64URL.encode(BigInteger.valueOf(17L)), Base64URL.encode(BigInteger.valueOf(3L)))
        .privateExponent(Base64URL.encode(BigInteger.valueOf(11L)))
        .keyID("key-1")
        .build();
  }
}
