package com.taskmigo.console.access.internal.persistence.signing;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.taskmigo.console.access.internal.domain.signing.SigningKey;
import java.text.ParseException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DatabaseJwkSource implements JWKSource<SecurityContext> {
  private final SigningKeyRepository signingKeys;

  public DatabaseJwkSource(SigningKeyRepository signingKeys) {
    this.signingKeys = signingKeys;
  }

  @Override
  public List<JWK> get(JWKSelector selector, SecurityContext context) throws KeySourceException {
    try {
      List<JWK> keys =
          this.signingKeys.findAllByActiveTrue().stream()
              .map(SigningKey::getJwk)
              .map(DatabaseJwkSource::parseJwk)
              .toList();
      return selector.select(new JWKSet(keys));
    } catch (Exception exception) {
      throw new KeySourceException("Unable to load signing keys", exception);
    }
  }

  private static JWK parseJwk(String value) {
    try {
      return JWK.parse(value);
    } catch (ParseException exception) {
      throw new IllegalStateException("Invalid JWK stored in database", exception);
    }
  }
}
