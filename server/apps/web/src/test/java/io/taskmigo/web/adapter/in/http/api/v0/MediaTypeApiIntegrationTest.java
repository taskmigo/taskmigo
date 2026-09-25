package io.taskmigo.web.adapter.in.http.api.v0;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class MediaTypeApiIntegrationTest extends ApiIntegrationTestSupport {

    /**
     * Verifies that v0 endpoints reject clients that explicitly request a non-JSON response representation.
     *
     * Given: an authenticated GET request whose Accept header allows only text/plain.
     * Expect: the API returns HTTP 406 because v0 endpoints produce application/json only.
     */
    @Test
    @DisplayName("rejects response media types other than JSON")
    void shouldReturnNotAcceptableWhenAcceptDoesNotAllowJson() {
        // Arrange
        String path = "/api/v0/groups?page=1&pageSize=20";

        // Act
        int status = this.api().getStatus(path, MediaType.TEXT_PLAIN);

        // Assert
        assertThat(status).isEqualTo(HttpStatus.NOT_ACCEPTABLE.value());
    }

    /**
     * Verifies that v0 endpoints with request bodies reject non-JSON payload media types before deserialization.
     *
     * Given: an authenticated POST request with Content-Type text/plain and an otherwise valid JSON-shaped body.
     * Expect: the API returns HTTP 415 because request bodies are accepted as application/json only.
     */
    @Test
    @DisplayName("rejects request body media types other than JSON")
    void shouldReturnUnsupportedMediaTypeWhenRequestContentTypeIsNotJson() {
        // Arrange
        String path = "/api/v0/groups";
        String body = "{}";

        // Act
        int status = this.api().postStatus(path, MediaType.TEXT_PLAIN, body);

        // Assert
        assertThat(status).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
    }
}
