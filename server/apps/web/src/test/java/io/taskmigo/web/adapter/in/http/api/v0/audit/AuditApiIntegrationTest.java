package io.taskmigo.web.adapter.in.http.api.v0.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class AuditApiIntegrationTest extends ApiIntegrationTestSupport {

    private final JdbcTemplate jdbc;

    AuditApiIntegrationTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Verifies that audit pagination is applied after deterministic newest-first ordering.
     *
     * Given: three User audit rows where the newest two share the same operation timestamp.
     * Expect: page one with pageSize two uses id DESC as a deterministic tie-breaker and omits the oldest row.
     */
    @Test
    @DisplayName("returns user audit logs newest first before offset pagination")
    void shouldReturnNewestUserAuditLogsWhenPageIsRequested() {
        // Arrange
        UUID oldest = this.insertAudit(Instant.parse("2099-01-01T00:00:00Z"));
        Instant tiedTimestamp = Instant.parse("2099-01-03T00:00:00Z");
        UUID middle = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID newest = UUID.fromString("00000000-0000-0000-0000-000000000002");
        this.insertAudit(middle, tiedTimestamp);
        this.insertAudit(newest, tiedTimestamp);

        // Act
        String response = this.api().get("/api/v0/audit/user/logs?page=1&pageSize=2");

        // Assert
        assertThat(response).contains(newest.toString(), middle.toString()).doesNotContain(oldest.toString());
        assertThat(response.indexOf(newest.toString())).isLessThan(response.indexOf(middle.toString()));
        assertThat(response).contains("\"type\":\"offset\"", "\"currentPage\":1", "\"pageSize\":2");
    }

    /**
     * Verifies that unsupported audit entity types use the existing domain-validation error contract.
     *
     * Given: an authenticated request for the unsupported entity type "group".
     * Expect: the API rejects the request with HTTP 400 and the standard DOMAIN_BAD_REQUEST error code.
     */
    @Test
    @DisplayName("rejects unsupported audit entity types through the domain error contract")
    void shouldReturnBadRequestWhenAuditEntityTypeIsUnsupported() {
        // Arrange
        String path = "/api/v0/audit/group/logs";

        // Act + Assert
        assertThatThrownBy(() -> this.api().get(path)).isInstanceOfSatisfying(
            HttpClientErrorException.BadRequest.class,
            exception -> assertThat(exception.getResponseBodyAsString()).contains("DOMAIN_BAD_REQUEST")
        );
    }

    /**
     * Verifies that audit logs cannot be read without the existing authenticated authorization boundary.
     *
     * Given: an unauthenticated request to the public audit route.
     * Expect: the security filter chain rejects the request before audit data is returned.
     */
    @Test
    @DisplayName("rejects unauthenticated audit requests")
    void shouldRejectAuditLogsWhenCallerIsUnauthenticated() {
        // Arrange
        RestClient anonymous = RestClient.builder()
            .baseUrl("http://localhost:" + this.port())
            .build();

        // Act
        int status = anonymous
            .get()
            .uri("/api/v0/audit/user/logs")
            .exchange((request, response) -> response.getStatusCode().value());

        // Assert
        assertThat(status).isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }

    private UUID insertAudit(Instant occurredAt) {
        UUID id = UUID.randomUUID();
        this.insertAudit(id, occurredAt);
        return id;
    }

    private void insertAudit(UUID id, Instant occurredAt) {
        this.jdbc.update(
            """
            insert into audit_logs (
                id, entity_type, entity_id, actor_id, actor_username, occurred_at, changes_json
            ) values (?, 'user', ?, ?, 'audit-api-test', ?, '[]')
            """,
            id,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Timestamp.from(occurredAt)
        );
    }
}
