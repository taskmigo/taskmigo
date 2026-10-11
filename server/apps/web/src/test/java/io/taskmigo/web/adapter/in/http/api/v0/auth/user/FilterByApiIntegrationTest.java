package io.taskmigo.web.adapter.in.http.api.v0.auth.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FilterByApiIntegrationTest extends ApiIntegrationTestSupport {

    /**
     * Given: an authenticated list request whose filterBy expression is syntactically incomplete.
     * Expect: the HTTP adapter returns 400 instead of treating malformed input as an absent client filter.
     */
    @Test
    @DisplayName("returns bad request for malformed filterBy")
    void shouldReturnBadRequestWhenFilterByIsMalformed() {
        // Act
        int status = this.api().getStatus("/api/v0/users?filterBy=object.username%3D%3D");

        // Assert
        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    /**
     * Given: an authenticated list request whose filterBy attempts to access the trusted principal root.
     * Expect: the HTTP adapter returns 400 because client filtering exposes only object.*.
     */
    @Test
    @DisplayName("returns bad request when filterBy references principal")
    void shouldReturnBadRequestWhenFilterByReferencesPrincipal() {
        // Act
        int status = this.api().getStatus("/api/v0/users?filterBy=principal.username%3D%3D%22system%22");

        // Assert
        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
