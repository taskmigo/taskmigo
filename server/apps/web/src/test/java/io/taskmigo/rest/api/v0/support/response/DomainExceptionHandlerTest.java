package io.taskmigo.rest.api.v0.support.response;

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
     * Verifies that semantic invalid-input failures preserve the existing public API error contract.
     *
     * Given: a DomainException classified as INVALID_INPUT with message = "Invalid input".
     * Expect: the web adapter returns HTTP 400 with message code domain.bad_request and error code DOMAIN_BAD_REQUEST.
     */
    @Test
    @DisplayName("should preserve bad-request HTTP contract when domain failure is invalid input")
    void shouldPreserveBadRequestHttpContractWhenDomainFailureIsInvalidInput() {
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

    private static final class TestDomainException extends DomainException {

        private static final long serialVersionUID = 1L;

        private TestDomainException(DomainFailureType type, String message) {
            super(type, message);
        }
    }
}
