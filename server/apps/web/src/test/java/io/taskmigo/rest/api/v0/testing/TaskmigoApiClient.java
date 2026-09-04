package io.taskmigo.rest.api.v0.testing;

import java.net.URI;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/// Provides authenticated, typed access to Taskmigo's v0 HTTP APIs for integration tests.
///
/// The client obtains a client-credentials token during construction. HTTP failures intentionally propagate from
/// [RestClient] so callers can assert the API error contract.
@NullMarked
public final class TaskmigoApiClient {

    private final RestClient client;
    private final Roles roles;
    private final Groups groups;
    private final Users users;
    private final Statements statements;

    /// Authenticates a test client against a running Taskmigo server.
    ///
    /// @param baseUri the server URI, including its random test port
    /// @param credentials the OAuth client credentials configured for the test server
    public TaskmigoApiClient(URI baseUri, ClientCredentials credentials) {
        String token = Objects.requireNonNull(
            RestClient.builder()
                .baseUrl(baseUri.toString())
                .build()
                .post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth(credentials.clientId(), credentials.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&scope=" + credentials.scope())
                .retrieve()
                .body(TokenResponse.class)
        ).access_token();
        this.client = RestClient.builder()
            .baseUrl(baseUri.toString())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
        this.roles = new Roles();
        this.groups = new Groups();
        this.users = new Users();
        this.statements = new Statements();
    }

    /// Returns the typed client for Role creation.
    public Roles roles() {
        return this.roles;
    }

    /// Returns the typed client for Group creation.
    public Groups groups() {
        return this.groups;
    }

    /// Returns the typed client for User creation.
    public Users users() {
        return this.users;
    }

    /// Returns the typed client for Statement creation.
    public Statements statements() {
        return this.statements;
    }

    /// Sends an authenticated GET request for API assertions not yet covered by a typed endpoint client.
    ///
    /// @param path the absolute API path and optional query string
    /// @return the response body
    public String get(String path) {
        return Objects.requireNonNull(this.client.get().uri(path).retrieve().body(String.class));
    }

    /// Returns the HTTP status for an authenticated GET request.
    public int getStatus(String path) {
        return Objects.requireNonNull(
            this.client
                .get()
                .uri(path)
                .exchange((request, response) -> response.getStatusCode().value())
        );
    }

    private UUID create(String path, Object request) {
        return Objects.requireNonNull(
            this.client
                .post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CreatedResponse.class)
        )
            .data()
            .id();
    }

    private void patch(String path, Object request) {
        this.client
            .patch()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toBodilessEntity();
    }

    /// OAuth client credentials used by the integration-test server.
    public record ClientCredentials(String clientId, String clientSecret, String scope) {}

    /// Payload accepted by `POST /api/v0/roles`.
    public record CreateRoleRequest(String name, @Nullable String description, @Nullable Collection<UUID> roleIds) {}

    /// Payload accepted by `POST /api/v0/groups`.
    public record CreateGroupRequest(
        String name,
        @Nullable String description,
        @Nullable Collection<UUID> groupIds,
        @Nullable Collection<UUID> roleIds
    ) {}

    /// Payload accepted by `POST /api/v0/users`.
    public record CreateUserRequest(
        String username,
        @Nullable Set<String> emails,
        String firstName,
        String lastName,
        @Nullable Collection<UUID> roleIds,
        @Nullable Collection<UUID> groupIds
    ) {}

    /// Payload accepted by `POST /api/v0/statements`.
    public record CreateStatementRequest(
        String name,
        @Nullable String description,
        String effect,
        String scope,
        StatementTarget target,
        @Nullable String policy
    ) {}

    /// Payload accepted by the direct Statement-assignment PATCH APIs.
    public record ReplaceStatementsRequest(@Nullable Collection<UUID> statementIds) {}

    /// API target embedded in a Statement request.
    public record StatementTarget(StatementApiTarget api) {}

    /// HTTP method and regular-expression path embedded in a Statement target.
    public record StatementApiTarget(String method, String path) {}

    /// Creates Roles through the public HTTP API.
    public final class Roles {

        private Roles() {}

        /// Creates a Role and returns its server-assigned id.
        ///
        /// @param request the Role properties and optional direct child Roles
        /// @return the created Role id
        public UUID create(CreateRoleRequest request) {
            return TaskmigoApiClient.this.create("/api/v0/roles", request);
        }

        /// Replaces the complete set of Statements directly assigned to a Role.
        public void replaceStatements(UUID roleId, Collection<UUID> statementIds) {
            TaskmigoApiClient.this.patch(
                "/api/v0/roles/" + roleId + "/statements",
                new ReplaceStatementsRequest(statementIds)
            );
        }
    }

    /// Creates Groups through the public HTTP API.
    public final class Groups {

        private Groups() {}

        /// Creates a Group and returns its server-assigned id.
        ///
        /// @param request the Group properties and optional child Groups and Roles
        /// @return the created Group id
        public UUID create(CreateGroupRequest request) {
            return TaskmigoApiClient.this.create("/api/v0/groups", request);
        }
    }

    /// Creates Users through the public HTTP API.
    public final class Users {

        private Users() {}

        /// Creates a User and returns its server-assigned id.
        ///
        /// @param request the User profile and optional direct Role and Group assignments
        /// @return the created User id
        public UUID create(CreateUserRequest request) {
            return TaskmigoApiClient.this.create("/api/v0/users", request);
        }

        /// Replaces the complete set of Statements directly assigned to a User.
        public void replaceStatements(UUID userId, Collection<UUID> statementIds) {
            TaskmigoApiClient.this.patch(
                "/api/v0/users/" + userId + "/statements",
                new ReplaceStatementsRequest(statementIds)
            );
        }
    }

    /// Creates Statements through the public HTTP API.
    public final class Statements {

        private Statements() {}

        /// Creates a Statement and returns its server-assigned id.
        public UUID create(CreateStatementRequest request) {
            return TaskmigoApiClient.this.create("/api/v0/statements", request);
        }
    }

    private record CreatedResponse(CreatedResource data) {}

    private record CreatedResource(UUID id) {}

    private record TokenResponse(String access_token) {}
}
