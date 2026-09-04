package io.taskmigo.rest.api.v0.auth.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.rest.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.CreateStatementRequest;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.StatementApiTarget;
import io.taskmigo.rest.api.v0.testing.TaskmigoApiClient.StatementTarget;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementApiIntegrationTest extends ApiIntegrationTestSupport {

    /**
     * Verifies that the public API persists and returns the canonical Statement representation.
     *
     * Given: a request Statement with an unconditional JavaScript policy.
     * Expect: creation returns an id and listing exposes the canonical scope, target, and policy.
     */
    @Test
    @DisplayName("creates and lists a canonical statement")
    void shouldCreateAndListStatementWhenRequestIsValid() {
        // Arrange
        CreateStatementRequest request = new CreateStatementRequest(
            "users_read",
            "Read users",
            "allow",
            "request",
            new StatementTarget(new StatementApiTarget("GET", "/api/v0/users")),
            "export default ({ request }) => request.path === '/api/v0/users';"
        );

        // Act
        this.api().statements().create(request);
        String response = this.findStatement("users_read");

        // Assert
        assertThat(response)
            .contains("\"name\":\"users_read\"")
            .contains("\"method\":\"GET\"")
            .contains("\"scope\":\"REQUEST\"")
            .contains("export default ({ request }) => request.path === '/api/v0/users';");
    }

    /**
     * Verifies that the public API accepts a Statement without a policy and preserves its unconditional form.
     *
     * Given: a valid request Statement whose policy is omitted.
     * Expect: creation succeeds and the listed canonical Statement has a null policy.
     */
    @Test
    @DisplayName("creates an unconditional statement when policy is omitted")
    void shouldCreateUnconditionalStatementWhenPolicyIsOmitted() {
        // Arrange
        CreateStatementRequest request = new CreateStatementRequest(
            "users_all",
            null,
            "allow",
            "request",
            new StatementTarget(new StatementApiTarget("GET", "/api/v0/users")),
            null
        );

        // Act
        this.api().statements().create(request);
        String response = this.findStatement("users_all");

        // Assert
        assertThat(response)
            .contains("\"scope\":\"REQUEST\"")
            .contains("\"policy\":null");
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
            "request",
            new StatementTarget(new StatementApiTarget("GET", "/api/v0/statements")),
            null
        );
    }

    private String findStatement(String name) {
        for (int page = 1; page <= 100; page++) {
            String response = this.api().get("/api/v0/statements?page=" + page + "&pageSize=100");
            if (response.contains("\"name\":\"" + name + "\"")) return response;
        }
        throw new AssertionError("Statement was not found in the paginated collection: " + name);
    }
}
