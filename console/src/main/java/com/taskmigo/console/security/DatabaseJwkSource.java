package com.taskmigo.console.security;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

@Component
public class DatabaseJwkSource implements JWKSource<SecurityContext> {

    private final JdbcOperations jdbc;

    public DatabaseJwkSource(JdbcOperations jdbc) {
        this.jdbc = jdbc;
        ensureSigningKeyExists();
    }

    @Override
    public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) throws KeySourceException {
        try {
            List<JWK> keys = jdbc.query(
                    "select jwk from oauth2_signing_key where active = true",
                    (resultSet, rowNumber) -> parseJwk(resultSet.getString("jwk")));
            return jwkSelector.select(new JWKSet(keys));
        } catch (Exception exception) {
            throw new KeySourceException("Unable to load signing keys", exception);
        }
    }

    private static JWK parseJwk(String value) {
        try {
            return JWK.parse(value);
        } catch (java.text.ParseException exception) {
            throw new IllegalStateException("Invalid JWK stored in database", exception);
        }
    }

    private void ensureSigningKeyExists() {
        Integer count = jdbc.queryForObject(
                "select count(*) from oauth2_signing_key where active = true", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        RSAKey key = generateRsaKey();
        try {
            jdbc.update("insert into oauth2_signing_key (key_id, jwk, active) values (?, ?, true)",
                    key.getKeyID(), key.toJSONString());
        } catch (DuplicateKeyException ignored) {
            // Another replica created the singleton active key concurrently.
        }
    }

    private static RSAKey generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            var pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate signing key", exception);
        }
    }
}
