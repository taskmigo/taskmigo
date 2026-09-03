package io.taskmigo.internal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SigningKeyStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("requires a provisioned signing key by default")
    void shouldRequireProvisionedKeyWhenDevelopmentCreationIsDisabled() {
        Path keyFile = this.temporaryDirectory.resolve("missing.pem");

        assertThatThrownBy(() -> SigningKeyStore.load(keyFile, "primary", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("reuses one private key during concurrent development creation")
    void shouldReusePrivateKeyWhenDevelopmentCreationIsConcurrent() throws Exception {
        Path keyFile = this.temporaryDirectory.resolve("oauth-signing-key.pem");

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> SigningKeyStore.load(keyFile, "primary", true));
            var second = executor.submit(() -> SigningKeyStore.load(keyFile, "primary", true));

            assertThat(second.get().getPrivateExponent()).isEqualTo(first.get().getPrivateExponent());
        }
        assertThat(keyFile).exists();
    }

    @Test
    @DisplayName("rejects a corrupt provisioned signing key")
    void shouldRejectProvisionedKeyWhenKeyMaterialIsCorrupt() throws Exception {
        Path keyFile = this.temporaryDirectory.resolve("corrupt.pem");
        Files.writeString(keyFile, "not-a-private-key");

        assertThatThrownBy(() -> SigningKeyStore.load(keyFile, "primary", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to load OAuth signing key");
    }
}
