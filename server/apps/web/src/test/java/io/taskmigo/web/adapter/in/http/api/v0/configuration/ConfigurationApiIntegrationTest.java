package io.taskmigo.web.adapter.in.http.api.v0.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class ConfigurationApiIntegrationTest extends ApiIntegrationTestSupport {

    private final JdbcTemplate jdbc;

    ConfigurationApiIntegrationTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @BeforeEach
    void resetRetention() {
        this.jdbc.update(
            """
            insert into application_configuration (configuration_key, configuration_value)
            values ('retention.user', 'P30D')
            on conflict (configuration_key)
            do update set configuration_value = excluded.configuration_value
            """
        );
    }

    /**
     * Given: no persisted override exists.
     * Expect: GET returns the documented P30D default.
     */
    @Test
    @DisplayName("returns the default retention when no row is persisted")
    void shouldReturnDefaultRetentionWhenConfigurationRowIsMissing() {
        this.jdbc.update("delete from application_configuration where configuration_key = 'retention.user'");

        String response = this.api().get("/api/v0/configuration");

        assertThat(response).contains("\"user\":\"P30D\"");
    }

    @ParameterizedTest
    @ValueSource(strings = { "P0D", "P1D", "PT24H", "P30D" })
    @DisplayName("accepts supported whole-day retention durations")
    void shouldAcceptSupportedRetentionDurations(String value) {
        this.api().patchJson("/api/v0/configuration", "{\"retention\":{\"user\":\"" + value + "\"}}");

        String expected = value.equals("PT24H") ? "P1D" : value;
        assertThat(this.api().get("/api/v0/configuration")).contains("\"user\":\"" + expected + "\"");
        assertThat(
            this.jdbc.queryForObject(
                "select configuration_value from application_configuration where configuration_key = 'retention.user'",
                String.class
            )
        ).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "-P1D", "PT1H", "PT36H", "P1DT12H" })
    @DisplayName("rejects unsupported retention durations")
    void shouldRejectUnsupportedRetentionDurations(String value) {
        assertThatThrownBy(() ->
            this.api().patchJson("/api/v0/configuration", "{\"retention\":{\"user\":\"" + value + "\"}}")
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);

        assertThat(
            this.jdbc.queryForObject(
                "select configuration_value from application_configuration where configuration_key = 'retention.user'",
                String.class
            )
        ).isEqualTo("P30D");
    }

    @Test
    @DisplayName("rejects unknown root and nested configuration properties")
    void shouldRejectUnknownConfigurationProperties() {
        assertThatThrownBy(() -> this.api().patchJson("/api/v0/configuration", "{\"unknown\":true}")).isInstanceOf(
            HttpClientErrorException.BadRequest.class
        );

        assertThatThrownBy(() ->
            this.api().patchJson("/api/v0/configuration", "{\"retention\":{\"unknown\":true}}")
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @DisplayName("leaves omitted retention values unchanged")
    void shouldLeaveOmittedValuesUnchanged() {
        this.api().patchJson("/api/v0/configuration", "{}");

        assertThat(this.api().get("/api/v0/configuration")).contains("\"user\":\"P30D\"");
    }

    @Test
    @DisplayName("rejects unauthenticated configuration access")
    void shouldRejectUnauthenticatedConfigurationAccess() {
        RestClient anonymous = RestClient.builder()
            .baseUrl("http://localhost:" + this.port())
            .build();

        int getStatus = anonymous
            .get()
            .uri("/api/v0/configuration")
            .exchange((request, response) -> response.getStatusCode().value());
        int patchStatus = anonymous
            .patch()
            .uri("/api/v0/configuration")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"retention\":{\"user\":\"P1D\"}}")
            .exchange((request, response) -> response.getStatusCode().value());

        assertThat(getStatus).isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
        assertThat(patchStatus).isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }

    /**
     * Verifies that changing retention configuration does not rewrite a retained User's existing retention instant.
     *
     * Given: a retained User whose retainedAt is exactly 2026-09-01T00:00:00Z.
     * Expect: changing the configured retention duration leaves that exact persisted instant unchanged.
     */
    @Test
    @DisplayName("changing retention does not rewrite existing retainedAt")
    void shouldPreserveExistingRetentionTimestampWhenConfigurationChanges() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Instant retainedAt = Instant.parse("2026-09-01T00:00:00Z");
        this.jdbc.update(
            """
            insert into users (id, username, first_name, last_name, status, retained_at)
            values (?, ?, 'Retained', 'User', 'RETAINED', ?::timestamptz)
            """,
            userId,
            "retained-" + userId,
            retainedAt.toString()
        );

        // Act
        this.api().patchJson("/api/v0/configuration", "{\"retention\":{\"user\":\"P1D\"}}");

        // Assert
        Timestamp persistedRetainedAt = Objects.requireNonNull(
            this.jdbc.queryForObject("select retained_at from users where id = ?", Timestamp.class, userId)
        );
        assertThat(persistedRetainedAt.toInstant()).isEqualTo(retainedAt);
    }
}
