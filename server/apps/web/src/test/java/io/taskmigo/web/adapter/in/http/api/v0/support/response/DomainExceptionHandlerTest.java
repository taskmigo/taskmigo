package io.taskmigo.web.adapter.in.http.api.v0.support.response;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class DomainExceptionHandlerTest {

    /**
     * Given: a DomainException classified as INVALID_INPUT with message = "Invalid input".
     * Expect: the web adapter preserves HTTP 400 for invalid request/domain input outside mutation semantics.
     */
    @Test
    @DisplayName("preserves bad request when domain failure is invalid input")
    void shouldPreserveBadRequestWhenDomainFailureIsInvalidInput() {
        // Arrange
        DomainExceptionHandler handler = new DomainExceptionHandler(
            new ApiResponseFactory(new MockHttpServletRequest())
        );
        DomainException exception = new TestDomainException(DomainFailureType.INVALID_INPUT, "Invalid input");

        // Act
        var response = handler.domain(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var body = Objects.requireNonNull(response.getBody());
        var error = Objects.requireNonNull(body.error());
        assertThat(body.message().code()).isEqualTo("domain.bad_request");
        assertThat(error.code()).isEqualTo("DOMAIN_BAD_REQUEST");
    }

    /**
     * Given: authorized target resolution succeeded but domain mutation validation is semantically invalid.
     * Expect: the web adapter returns HTTP 422 Unprocessable Content.
     */
    @Test
    @DisplayName("maps semantic mutation invalidity to unprocessable content")
    void shouldReturnUnprocessableContentWhenDomainFailureIsUnprocessable() {
        // Arrange
        DomainExceptionHandler handler = new DomainExceptionHandler(
            new ApiResponseFactory(new MockHttpServletRequest())
        );
        DomainException exception = new TestDomainException(
            DomainFailureType.UNPROCESSABLE,
            "One or more Statements do not exist"
        );

        // Act
        var response = handler.domain(exception);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        var body = Objects.requireNonNull(response.getBody());
        var error = Objects.requireNonNull(body.error());
        assertThat(body.message().code()).isEqualTo("domain.unprocessable_content");
        assertThat(error.code()).isEqualTo("DOMAIN_UNPROCESSABLE_CONTENT");
    }

    private static final class TestDomainException extends DomainException {

        private static final long serialVersionUID = 1L;

        private TestDomainException(DomainFailureType type, String message) {
            super(type, message);
        }
    }
}
