package com.taskmigo.console.access.internal.persistence.signing;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.taskmigo.console.access.internal.domain.signing.SigningKey;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.List;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DatabaseJwkSourceTest {
  private final SigningKeyRepository repository =
      (SigningKeyRepository) Mockito.mock(SigningKeyRepository.class);
  private final DatabaseJwkSource source = new DatabaseJwkSource(this.repository);

  DatabaseJwkSourceTest() {}

  @Test
  void selectsMatchingKeyFromMultipleActiveKeys() throws Exception {
    Mockito.when(this.repository.findAllByActiveTrue())
        .thenReturn(
            List.of(
                DatabaseJwkSourceTest.stored(DatabaseJwkSourceTest.key("first")),
                DatabaseJwkSourceTest.stored(DatabaseJwkSourceTest.key("second"))));
    List<JWK> selected = this.source.get(DatabaseJwkSourceTest.selector("second"), null);
    Assertions.assertThat(selected)
        .extracting(jwk -> jwk.getKeyID())
        .containsExactly(new String[] {"second"});
  }

  @Test
  void returnsEmptyWhenNoKeyMatches() throws Exception {
    Mockito.when(this.repository.findAllByActiveTrue())
        .thenReturn(List.of(DatabaseJwkSourceTest.stored(DatabaseJwkSourceTest.key("first"))));
    Assertions.assertThat(this.source.get(DatabaseJwkSourceTest.selector("missing"), null))
        .isEmpty();
  }

  @Test
  void wrapsInvalidPersistedJwk() {
    Mockito.when(this.repository.findAllByActiveTrue())
        .thenReturn(List.of(new SigningKey("broken", "not-json")));
    ((AbstractThrowableAssert)
            Assertions.assertThatThrownBy(
                    () -> this.source.get(DatabaseJwkSourceTest.selector("broken"), null))
                .isInstanceOf(KeySourceException.class))
        .hasMessage("Unable to load signing keys")
        .hasRootCauseInstanceOf(ParseException.class);
  }

  @Test
  void wrapsPersistenceFailure() {
    Mockito.when(this.repository.findAllByActiveTrue())
        .thenThrow(new Throwable[] {new IllegalStateException("database")});
    ((AbstractThrowableAssert)
            Assertions.assertThatThrownBy(
                    () -> this.source.get(DatabaseJwkSourceTest.selector("any"), null))
                .isInstanceOf(KeySourceException.class))
        .hasRootCauseMessage("database");
  }

  private static SigningKey stored(RSAKey key) {
    return new SigningKey(key.getKeyID(), key.toJSONString());
  }

  private static RSAKey key(String id) {
    return new RSAKey.Builder(
            Base64URL.encode(BigInteger.valueOf(3233L)), Base64URL.encode(BigInteger.valueOf(17L)))
        .privateExponent(Base64URL.encode(BigInteger.valueOf(2753L)))
        .keyID(id)
        .build();
  }

  private static JWKSelector selector(String id) {
    return new JWKSelector(new JWKMatcher.Builder().keyID(id).build());
  }
}
