package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseUtcIntegrationTest extends ApiIntegrationTestSupport {

    private final JdbcTemplate jdbc;

    DatabaseUtcIntegrationTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Verifies that application-owned PostgreSQL sessions interact with temporal values in UTC.
     *
     * Given: the web application has started with its production datasource configuration.
     * Expect: PostgreSQL reports UTC as the effective timezone for a pooled application session.
     */
    @Test
    @DisplayName("uses UTC for PostgreSQL sessions")
    void shouldUseUtcWhenDatabaseSessionIsCreated() {
        // Arrange
        ZoneOffset expectedOffset = ZoneOffset.UTC;

        // Act
        String timeZone = Objects.requireNonNull(this.jdbc.queryForObject("show time zone", String.class));

        // Assert
        assertThat(ZoneId.of(timeZone).normalized()).isEqualTo(expectedOffset);
    }
}
