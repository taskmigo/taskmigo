package com.taskmigo.console.access.internal.application.signing;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RsaSigningKeyGenerator implements SigningKeyGenerator {
  private final KeyPairGeneratorFactory generators;

  public RsaSigningKeyGenerator(KeyPairGeneratorFactory generators) {
    this.generators = generators;
  }

  @Override
  public RSAKey generate() {
    try {
      KeyPairGenerator generator = this.generators.create();
      generator.initialize(3072);
      KeyPair pair = generator.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
          .privateKey((RSAPrivateKey) pair.getPrivate())
          .keyID(UUID.randomUUID().toString())
          .build();
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to generate signing key", exception);
    }
  }
}
