package io.taskmigo.rest.api.v0;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.rest.api.v0.testing.ApiIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiVersioningIntegrationTest extends ApiIntegrationTestSupport {

    /**
     * Verifies that Spring rejects an API path whose version is not declared by a controller mapping.
     *
     * Given: an authenticated GET request for `/api/v1/groups`, while only v0 is supported.
     * Expect: Spring returns HTTP 400 for the unsupported API version.
     */
    @Test
    @DisplayName("rejects an unsupported API version")
    void shouldRejectUnsupportedVersionWhenVersionIsNotDeclared() {
        // Arrange
        String unsupportedPath = "/api/v1/groups";

        // Act
        int status = this.api().getStatus(unsupportedPath);

        // Assert
        assertThat(status).isEqualTo(400);
    }
}
