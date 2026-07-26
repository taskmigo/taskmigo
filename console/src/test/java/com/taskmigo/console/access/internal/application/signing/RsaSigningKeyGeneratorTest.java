package com.taskmigo.console.access.internal.application.signing;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.GeneralSecurityException;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class RsaSigningKeyGeneratorTest {
  RsaSigningKeyGeneratorTest() {}

  @Test
  void createsPrivateRsaKeySuitableForSigning() {
    RSAKey key =
        new RsaSigningKeyGenerator((KeyPairGeneratorFactory) new DefaultKeyPairGeneratorFactory())
            .generate();
    Assertions.assertThat((String) key.getKeyID()).isNotBlank();
    Assertions.assertThat((boolean) key.isPrivate()).isTrue();
    Assertions.assertThat((int) key.size()).isGreaterThanOrEqualTo(3072);
  }

  @Test
  void wrapsCryptographicFailure() {
    RsaSigningKeyGenerator generator =
        new RsaSigningKeyGenerator(
            () -> {
              throw new GeneralSecurityException("provider unavailable");
            });
    ((AbstractThrowableAssert)
            Assertions.assertThatThrownBy(() -> ((RsaSigningKeyGenerator) generator).generate())
                .isInstanceOf(IllegalStateException.class))
        .hasMessage("Unable to generate signing key")
        .hasRootCauseMessage("provider unavailable");
  }
}
