package io.taskmigo.web.api.v0.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.web.api.v0.testing.ApiIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

class SortByApiIntegrationTest extends ApiIntegrationTestSupport {

    /**
     * Given: an authenticated list request whose sortBy references an unsupported field.
     * Expect: the HTTP adapter returns the sort-specific 400 error contract rather than a filterBy error.
     */
    @Test
    @DisplayName("returns sort-specific bad request for invalid sortBy")
    void shouldReturnSortSpecificBadRequestWhenSortByIsInvalid() {
        // Act / Assert
        assertThatThrownBy(() -> this.api().get("/api/v0/users?sortBy=missing")).isInstanceOfSatisfying(
            HttpClientErrorException.BadRequest.class,
            exception ->
                assertThat(exception.getResponseBodyAsString())
                    .contains("\"code\":\"query.sort.invalid\"")
                    .contains("\"code\":\"INVALID_SORT\"")
                    .doesNotContain("INVALID_FILTER")
        );
    }
}
