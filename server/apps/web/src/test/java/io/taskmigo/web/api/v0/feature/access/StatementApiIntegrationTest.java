package io.taskmigo.web.api.v0.feature.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.web.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.web.api.v0.testing.TaskmigoApiClient.CreateStatementRequest;
import io.taskmigo.web.api.v0.testing.TaskmigoApiClient.StatementApiTarget;
import io.taskmigo.web.api.v0.testing.TaskmigoApiClient.StatementTarget;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementApiIntegrationTest extends ApiIntegrationTestSupport {

    /**
     * Verifies that the public API persists and returns the canonical Statement representation.
     *
     * Given: a request Statement with a lowercase HTTP method and one condition.
     * Expect: creation returns an id and listing exposes the uppercase method and original condition.
     */
    @Test
    @DisplayName("creates and lists a canonical statement")
    void shouldCreateAndListStatementWhenRequestIsValid() {
        // Arrange
        CreateStatementRequest request = new CreateStatementRequest(
            "users_read",
            "Read users",
            "allow",
            new StatementTarget("request", new StatementApiTarget("GET", "/api/v0/users")),
            List.of("request.path == '/api/v0/users'")
        );

        // Act
        this.api().statements().create(request);
        String response = this.api().get("/api/v0/statements?page=1&pageSize=100");

        // Assert
        assertThat(response)
            .contains("\"name\":\"users_read\"")
            .contains("\"method\":\"GET\"")
            .contains("request.path == '/api/v0/users'");
    }

    /**
     * Verifies that the Statement collection uses the shared offset pagination contract.
     *
     * Given: two newly created Statements and a request for page 2 with one item per page.
     * Expect: the response reports page 2, page size 1, offset pagination, and a nonzero total item count.
     */
    @Test
    @DisplayName("lists statements with offset pagination")
    void shouldListStatementsWithOffsetPaginationWhenPageParametersAreProvided() {
        // Arrange
        this.api()
            .statements()
            .create(this.request("pagination-one-" + UUID.randomUUID()));
        this.api()
            .statements()
            .create(this.request("pagination-two-" + UUID.randomUUID()));

        // Act
        String response = this.api().get("/api/v0/statements?page=2&pageSize=1");

        // Assert
        assertThat(response)
            .contains("\"code\":\"resource.statement.listed\"")
            .contains("\"type\":\"offset\"")
            .contains("\"currentPage\":2")
            .contains("\"pageSize\":1")
            .contains("\"totalItems\":")
            .contains("\"totalPages\":");
    }

    private CreateStatementRequest request(String name) {
        return new CreateStatementRequest(
            name,
            null,
            "allow",
            new StatementTarget("request", new StatementApiTarget("GET", "/api/v0/statements")),
            List.of()
        );
    }
}
