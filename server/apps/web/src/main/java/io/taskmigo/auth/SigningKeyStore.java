package io.taskmigo.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Objects;

final class SigningKeyStore {

    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

    private SigningKeyStore() {}

    static synchronized RSAKey load(Path configuredPath, String keyId, boolean createIfMissing) {
        if (keyId.isBlank()) throw new IllegalStateException("OAuth signing key id must not be blank");
        var path = configuredPath.toAbsolutePath().normalize();
        try {
            if (!Files.exists(path)) {
                if (!createIfMissing) {
                    throw new IllegalStateException("OAuth signing key does not exist: " + path);
                }
                createForDevelopment(path);
            }
            return jwk(readPrivateKey(path), keyId);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load OAuth signing key from " + path, exception);
        }
    }

    private static void createForDevelopment(Path path) throws GeneralSecurityException, IOException {
        var parent = Objects.requireNonNull(path.getParent());
        Files.createDirectories(parent);
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        var keyPair = generator.generateKeyPair();

        var temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            restrictToOwner(temporary);
            Files.writeString(temporary, pem(keyPair.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
            try {
                Files.move(temporary, path);
            } catch (FileAlreadyExistsException _) {
                // Another development process created a complete key first; use that file.
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        restrictToOwner(path);
    }

    private static void restrictToOwner(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException _) {
            // Non-POSIX file systems use their platform-specific file permissions.
        }
    }

    private static RSAPrivateCrtKey readPrivateKey(Path path) throws IOException, GeneralSecurityException {
        var encoded = Files.readString(path, StandardCharsets.US_ASCII)
            .replace(PRIVATE_KEY_BEGIN, "")
            .replace(PRIVATE_KEY_END, "")
            .replaceAll("\\s", "");
        var der = Base64.getDecoder().decode(encoded);
        return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static RSAKey jwk(RSAPrivateCrtKey privateKey, String keyId) throws GeneralSecurityException {
        var publicKeySpec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
        var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
        return new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID(keyId)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();
    }

    private static String pem(byte[] der) {
        var encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return PRIVATE_KEY_BEGIN + "\n" + encoded + "\n" + PRIVATE_KEY_END + "\n";
    }
}
