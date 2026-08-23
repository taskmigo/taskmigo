package io.taskmigo.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SigningKeyStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reusesThePersistedPrivateKey() {
        Path keyFile = temporaryDirectory.resolve("oauth-signing-key.pem");

        var first = SigningKeyStore.loadOrCreate(keyFile, "primary");
        var second = SigningKeyStore.loadOrCreate(keyFile, "primary");

        assertThat(keyFile).exists();
        assertThat(second.getModulus()).isEqualTo(first.getModulus());
        assertThat(second.getPrivateExponent()).isEqualTo(first.getPrivateExponent());
        assertThat(second.getKeyID()).isEqualTo("primary");
    }
}
