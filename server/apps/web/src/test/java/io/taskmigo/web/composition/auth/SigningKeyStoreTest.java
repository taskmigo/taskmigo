package io.taskmigo.web.composition.auth;

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

    /**
     * Verifies that production-style configuration does not silently create missing OAuth signing material.
     *
     * Given: a missing key path with development creation disabled.
     * Expect: loading fails with an error identifying that the signing key does not exist.
     */
    @Test
    @DisplayName("requires a provisioned signing key by default")
    void shouldRequireProvisionedKeyWhenDevelopmentCreationIsDisabled() {
        // Arrange
        Path keyFile = this.temporaryDirectory.resolve("missing.pem");

        // Act + Assert
        assertThatThrownBy(() -> SigningKeyStore.load(keyFile, "primary", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not exist");
    }

    /**
     * Verifies that concurrent development startup converges on one complete signing key.
     *
     * Given: two concurrent loads for the same missing key path with development creation enabled.
     * Expect: both callers observe the same private key and the key file exists after creation.
     */
    @Test
    @DisplayName("reuses one private key during concurrent development creation")
    void shouldReusePrivateKeyWhenDevelopmentCreationIsConcurrent() throws Exception {
        // Arrange
        Path keyFile = this.temporaryDirectory.resolve("oauth-signing-key.pem");

        // Act
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> SigningKeyStore.load(keyFile, "primary", true));
            var second = executor.submit(() -> SigningKeyStore.load(keyFile, "primary", true));

            // Assert
            assertThat(second.get().getPrivateExponent()).isEqualTo(first.get().getPrivateExponent());
        }
        assertThat(keyFile).exists();
    }

    /**
     * Verifies that corrupt provisioned signing material is rejected rather than replaced.
     *
     * Given: an existing signing-key file containing invalid private-key data.
     * Expect: loading fails with an error that identifies the configured signing-key path.
     */
    @Test
    @DisplayName("rejects a corrupt provisioned signing key")
    void shouldRejectProvisionedKeyWhenKeyMaterialIsCorrupt() throws Exception {
        // Arrange
        Path keyFile = this.temporaryDirectory.resolve("corrupt.pem");
        Files.writeString(keyFile, "not-a-private-key");

        // Act + Assert
        assertThatThrownBy(() -> SigningKeyStore.load(keyFile, "primary", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to load OAuth signing key");
    }
}
