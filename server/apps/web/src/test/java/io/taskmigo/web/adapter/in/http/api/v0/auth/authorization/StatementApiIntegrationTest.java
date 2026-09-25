package io.taskmigo.web.adapter.in.http.api.v0.auth.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateStatementRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.StatementApiTarget;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.StatementTarget;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

class StatementApiIntegrationTest extends ApiIntegrationTestSupport {

    /**
     * Verifies that the public API persists and returns the canonical Statement representation.
     *
     * Given: a request Statement with an unconditional Embedded Language policy.
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
            "return request.path == \"/api/v0/users\";"
        );

        // Act
        this.api().statements().create(request);
        String response = this.findStatement("users_read");

        // Assert
        assertThat(response)
            .contains("\"code\":\"users_read\"")
            .contains("\"method\":\"GET\"")
            .contains("\"scope\":\"REQUEST\"")
            .contains("return request.path == \\\"/api/v0/users\\\";");
    }

    /**
     * Verifies that the public API rejects a Statement with a blank required policy source.
     *
     * Given: a request Statement whose policy is an empty string.
     * Expect: creation fails with an unprocessable-content response because blank policy is structurally invalid.
     */
    @Test
    @DisplayName("rejects a statement when policy is blank")
    void shouldRejectStatementWhenPolicyIsBlank() {
        // Arrange
        CreateStatementRequest request = new CreateStatementRequest(
            "users_all",
            null,
            "allow",
            "request",
            new StatementTarget(new StatementApiTarget("GET", "/api/v0/users")),
            ""
        );

        // Act + Assert
        assertThatThrownBy(() -> this.api().statements().create(request))
            .isInstanceOf(HttpClientErrorException.UnprocessableContent.class)
            .hasMessageContaining("policy");
    }

    /**
     * Verifies the Phase 4 decision that policy and target semantics are deferred until authorization runtime.
     *
     * Given: a structurally valid Statement with malformed regex target and malformed policy syntax.
     * Expect: creation succeeds and listing returns the raw canonical source unchanged.
     */
    @Test
    @DisplayName("persists semantically invalid statement for deferred runtime validation")
    void shouldPersistStatementWhenSemanticValidationIsDeferred() {
        // Arrange
        String code = "deferred-" + UUID.randomUUID();
        CreateStatementRequest request = new CreateStatementRequest(
            code,
            null,
            "allow",
            "request",
            new StatementTarget(new StatementApiTarget("GET", "[")),
            "return request.method == ;"
        );

        // Act
        this.api().statements().create(request);
        String response = this.findStatement(code);

        // Assert
        assertThat(response)
            .contains("\"code\":\"" + code + "\"")
            .contains("\"path\":\"[\"")
            .contains("return request.method == ;");
    }

    /**
     * Verifies removed or unsupported policy constructs are persistence input rather than create-time semantics.
     *
     * Given: a Statement containing a removed resource intrinsic that cannot compile for authorization.
     * Expect: creation succeeds; authorization runtime owns the eventual fail-closed semantic validation.
     */
    @Test
    @DisplayName("persists unsupported policy constructs until authorization runtime")
    void shouldPersistStatementWhenPolicyUsesUnsupportedRuntimeConstructs() {
        // Arrange
        String code = "removed-resource-" + UUID.randomUUID();
        CreateStatementRequest request = new CreateStatementRequest(
            code,
            null,
            "allow",
            "request",
            new StatementTarget(new StatementApiTarget("GET", "/api/v0/users")),
            "return resource(\"user\", \"id\");"
        );

        // Act
        this.api().statements().create(request);
        String response = this.findStatement(code);

        // Assert
        assertThat(response)
            .contains("\"code\":\"" + code + "\"")
            .contains("resource(\\\"user\\\", \\\"id\\\")");
    }

    /**
     * Verifies that duplicate runtime Statement codes remain a stable client failure through the public API.
     *
     * Given: a Statement has already been created through the public API with a generated code.
     * Expect: creating another Statement with the same code returns HTTP 400 with the duplicate-code message.
     */
    @Test
    @DisplayName("rejects duplicate statement codes as a client failure")
    void shouldReturnBadRequestWhenStatementCodeAlreadyExists() {
        // Arrange
        CreateStatementRequest request = this.request("duplicate-" + UUID.randomUUID());
        this.api().statements().create(request);

        // Act + Assert
        assertThatThrownBy(() -> this.api().statements().create(request)).isInstanceOfSatisfying(
            HttpClientErrorException.BadRequest.class,
            exception -> assertThat(exception.getResponseBodyAsString()).contains("Statement code already exists")
        );
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
            "return true;"
        );
    }

    private String findStatement(String name) {
        for (int page = 1; page <= 100; page++) {
            String response = this.api().get("/api/v0/statements?page=" + page + "&pageSize=100");
            if (response.contains("\"code\":\"" + name + "\"")) {
                return response;
            }
        }
        throw new AssertionError("Statement was not found in the paginated collection: " + name);
    }
}
