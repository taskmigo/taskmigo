package io.taskmigo.web.adapter.in.http.api.v0.auth.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.request.application.port.out.EffectiveStatementResolver;
import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateRoleRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateStatementRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateUserRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.StatementApiTarget;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.StatementTarget;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class StatementApiIntegrationTest extends ApiIntegrationTestSupport {

    private final EffectiveStatementResolver statementResolver;
    private final JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    StatementApiIntegrationTest(EffectiveStatementResolver statementResolver, JdbcTemplate jdbc) {
        this.statementResolver = statementResolver;
        this.jdbc = jdbc;
    }

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
     * Verifies deletion removes the Statement and every direct assignment that references it.
     *
     * Given: one Statement assigned directly to a User and to a Role also assigned to that User.
     * Expect: deletion removes both bindings and the next effective-Statement resolution no longer contains it.
     */
    @Test
    @DisplayName("deletes a statement and removes its assignments")
    void shouldDeleteStatementAndAssignmentsWhenStatementExists() {
        // Arrange
        String code = "delete-" + UUID.randomUUID();
        UUID statement = this.api().statements().create(this.request(code));
        UUID role = this.api()
            .roles()
            .create(new CreateRoleRequest("DeleteStatementRole" + compactUuid(), null, Set.of()));
        this.api().roles().replaceStatements(role, Set.of(statement));
        String username = "statement-delete-" + compactUuid();
        UUID user = this.api()
            .users()
            .create(
                new CreateUserRequest(
                    username,
                    Set.of(username + "@example.com"),
                    "Test",
                    "User",
                    Set.of(role),
                    Set.of()
                )
            );
        this.api().users().replaceStatements(user, Set.of(statement));
        assertThat(this.statementResolver.resolve(user))
            .extracting(effective -> effective.statement().id())
            .contains(statement);

        // Act
        this.api().statements().delete(statement);

        // Assert
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from role_statements where statement_id = ?",
                Integer.class,
                statement
            )
        ).isZero();
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from subject_statement_bindings where statement_id = ?",
                Integer.class,
                statement
            )
        ).isZero();
        assertThat(this.statementResolver.resolve(user))
            .extracting(effective -> effective.statement().id())
            .doesNotContain(statement);
    }

    /**
     * Verifies deleting an unknown Statement preserves the public not-found contract.
     *
     * Given: a random Statement id that has never been persisted.
     * Expect: deletion returns HTTP 404 with the stable Statement-not-found message.
     */
    @Test
    @DisplayName("returns not found when deleting an unknown statement")
    void shouldReturnNotFoundWhenStatementDoesNotExist() {
        // Arrange
        UUID missing = UUID.randomUUID();

        // Act + Assert
        assertThatThrownBy(() -> this.api().statements().delete(missing)).isInstanceOfSatisfying(
            HttpClientErrorException.NotFound.class,
            exception -> assertThat(exception.getResponseBodyAsString()).contains("Statement not found")
        );
    }

    /**
     * Verifies Statement deletion remains protected by the normal API authentication boundary.
     *
     * Given: an existing Statement and a DELETE request with no Authorization header.
     * Expect: the request returns HTTP 401 and the Statement remains available afterwards.
     */
    @Test
    @DisplayName("rejects unauthenticated statement deletion")
    void shouldRejectStatementDeletionWhenCallerIsUnauthenticated() {
        // Arrange
        String code = "protected-delete-" + UUID.randomUUID();
        UUID statement = this.api().statements().create(this.request(code));
        RestClient unauthenticated = RestClient.create("http://localhost:" + this.port);

        // Act + Assert
        assertThatThrownBy(() ->
            unauthenticated
                .delete()
                .uri("/api/v0/statements/" + statement)
                .retrieve()
                .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThat(this.findStatement(code)).contains("\"code\":\"" + code + "\"");
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

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
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
