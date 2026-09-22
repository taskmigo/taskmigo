package io.taskmigo.web.adapter.in.http.api.v0.auth.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateStatementRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateUserRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.StatementApiTarget;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.StatementTarget;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AuthorizationApiIntegrationTest extends ApiIntegrationTestSupport {

    /**
     * Verifies that a matching Request DENY overrides the system User's existing Request ALLOW through the HTTP layer.
     *
     * Given: the authenticated system principal has its normal allow Role plus one direct unconditional GET deny.
     * Expect: GET /api/v0/users is rejected with HTTP 403 by the production security filter chain.
     */
    @Test
    @DisplayName("matching request deny overrides existing allows through HTTP")
    void shouldDenyRequestWhenMatchingDenyOverridesExistingAllow() {
        // Arrange
        UUID systemUserId = this.systemUserId();
        this.api().users().replaceStatements(systemUserId, List.of());
        UUID deny = this.createStatement("request-deny", "deny", "request", "return true;");
        this.api().users().replaceStatements(systemUserId, List.of(deny));

        try {
            // Act
            int status = this.api().getStatus("/api/v0/users?page=1&pageSize=100");

            // Assert
            assertThat(status).isEqualTo(403);
        } finally {
            this.api().users().replaceStatements(systemUserId, List.of());
        }
    }

    /**
     * Verifies that a matching-target Request DENY with a false policy does not suppress an existing Request ALLOW.
     *
     * Given: the authenticated system principal has its normal allow Role plus one GET deny whose policy only denies POST.
     * Expect: GET /api/v0/users remains allowed with HTTP 200.
     */
    @Test
    @DisplayName("non-matching request deny leaves an existing allow effective")
    void shouldAllowRequestWhenDenyPolicyDoesNotMatch() {
        // Arrange
        UUID systemUserId = this.systemUserId();
        this.api().users().replaceStatements(systemUserId, List.of());
        UUID deny = this.createStatement(
            "request-non-matching-deny",
            "deny",
            "request",
            "return request.method == \"POST\";"
        );
        this.api().users().replaceStatements(systemUserId, List.of(deny));

        try {
            // Act
            int status = this.api().getStatus("/api/v0/users?page=1&pageSize=100");

            // Assert
            assertThat(status).isEqualTo(200);
        } finally {
            this.api().users().replaceStatements(systemUserId, List.of());
        }
    }

    /**
     * Verifies that Object Authorization filtering occurs before offset pagination and pagination totals.
     *
     * Given: two fixture Users, the normal object-wide ALLOW, and a direct DENY for every User except one visible fixture.
     * Expect: pageSize=1 returns only the visible User with totalItems=1 and totalPages=1.
     */
    @Test
    @DisplayName("object authorization filters before offset pagination")
    void shouldFilterObjectsBeforePaginationWhenObjectDenyRestrictsVisibility() {
        // Arrange
        UUID systemUserId = this.systemUserId();
        this.api().users().replaceStatements(systemUserId, List.of());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String hiddenUsername = "phase1-hidden-" + suffix;
        String visibleUsername = "phase1-visible-" + suffix;
        this.createUser(hiddenUsername);
        this.createUser(visibleUsername);
        UUID deny = this.createStatement(
            "object-filter",
            "deny",
            "object",
            "return object.username != " + quoted(visibleUsername) + ";"
        );
        this.api().users().replaceStatements(systemUserId, List.of(deny));

        try {
            // Act
            JsonNode response = parse(this.api().get("/api/v0/users?page=1&pageSize=1"));

            // Assert
            JsonNode offset = response.path("meta").path("pagination").path("offset");
            assertThat(offset.path("currentPage").asInt()).isEqualTo(1);
            assertThat(offset.path("pageSize").asInt()).isEqualTo(1);
            assertThat(offset.path("totalItems").asInt()).isEqualTo(1);
            assertThat(offset.path("totalPages").asInt()).isEqualTo(1);
            assertThat(response.path("data").size()).isEqualTo(1);
            assertThat(response.path("data").get(0).path("username").asString()).isEqualTo(visibleUsername);
        } finally {
            this.api().users().replaceStatements(systemUserId, List.of());
        }
    }

    private UUID systemUserId() {
        JsonNode response = parse(this.api().get("/api/v0/users?page=1&pageSize=100"));
        for (JsonNode user : response.path("data")) {
            if ("system".equals(user.path("username").asString())) {
                return UUID.fromString(user.path("id").asString());
            }
        }
        throw new IllegalStateException("System User is not visible through the integration-test API");
    }

    private void createUser(String username) {
        this.api()
            .users()
            .create(
                new CreateUserRequest(username, Set.of(username + "@example.com"), "Phase", "One", Set.of(), Set.of())
            );
    }

    private UUID createStatement(String suffix, String effect, String scope, String policy) {
        String code = "phase1-" + suffix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return this.api()
            .statements()
            .create(
                new CreateStatementRequest(
                    code,
                    null,
                    effect,
                    scope,
                    new StatementTarget(new StatementApiTarget("GET", "/api/v0/users")),
                    policy
                )
            );
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static JsonNode parse(String body) {
        return JsonMapper.builder().build().readTree(body);
    }
}
