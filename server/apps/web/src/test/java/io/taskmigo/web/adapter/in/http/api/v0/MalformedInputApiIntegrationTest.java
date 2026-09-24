package io.taskmigo.web.adapter.in.http.api.v0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateRoleRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateUserRequest;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.HttpClientErrorException;

class MalformedInputApiIntegrationTest extends ApiIntegrationTestSupport {

    /**
     * Verifies that unsupported Statement effect and scope values are rejected before enum parsing reaches the
     * application call.
     *
     * Given: structurally valid Statement JSON whose effect or scope is outside the canonical lowercase values.
     * Expect: the API returns HTTP 422 with the validation error contract instead of HTTP 500.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unsupportedStatementValues")
    @DisplayName("rejects unsupported Statement effect and scope values")
    void shouldReturnUnprocessableContentWhenStatementEffectOrScopeIsUnsupported(String field, String body) {
        // Arrange
        String requestBody = body;

        // Act + Assert
        assertThatThrownBy(() -> this.api().postJson("/api/v0/statements", requestBody)).isInstanceOfSatisfying(
            HttpClientErrorException.UnprocessableContent.class,
            exception ->
                assertThat(exception.getResponseBodyAsString())
                    .contains("\"code\":\"VALIDATION_ERROR\"")
                    .contains(field)
        );
    }

    /**
     * Verifies that validation cascades into the nested Statement target.
     *
     * Given: structurally valid Statement JSON whose target object exists but target.api is null.
     * Expect: the API returns HTTP 422 with a validation error instead of dereferencing the null API target.
     */
    @Test
    @DisplayName("rejects a Statement whose nested API target is null")
    void shouldReturnUnprocessableContentWhenNestedStatementApiTargetIsNull() {
        // Arrange
        String body = """
        {
          "code": "invalid-target",
          "description": null,
          "effect": "allow",
          "scope": "request",
          "target": {"api": null},
          "policy": "return true;"
        }
        """;

        // Act + Assert
        assertThatThrownBy(() -> this.api().postJson("/api/v0/statements", body)).isInstanceOfSatisfying(
            HttpClientErrorException.UnprocessableContent.class,
            exception ->
                assertThat(exception.getResponseBodyAsString())
                    .contains("\"code\":\"VALIDATION_ERROR\"")
                    .contains("target.api")
        );
    }

    /**
     * Verifies that every UUID collection accepted by v0 create requests rejects null elements at the HTTP boundary.
     *
     * Given: Role, Group, or User creation JSON containing a null element in one UUID collection.
     * Expect: each API returns HTTP 422 with a validation error instead of passing null to Set.copyOf.
     */
    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("createRequestsWithNullUuidElements")
    @DisplayName("rejects null UUID elements in create requests")
    void shouldReturnUnprocessableContentWhenCreateRequestContainsNullUuidElement(
        String field,
        String path,
        String body
    ) {
        // Arrange
        String requestBody = body;

        // Act + Assert
        assertThatThrownBy(() -> this.api().postJson(path, requestBody)).isInstanceOfSatisfying(
            HttpClientErrorException.UnprocessableContent.class,
            exception ->
                assertThat(exception.getResponseBodyAsString())
                    .contains("\"code\":\"VALIDATION_ERROR\"")
                    .contains(field)
        );
    }

    /**
     * Verifies that direct Role-to-Statement assignment validates collection elements before application logic.
     *
     * Given: an existing Role and a replacement payload containing statementIds = [null].
     * Expect: the API returns HTTP 422 and the null identifier never reaches assignment services.
     */
    @Test
    @DisplayName("rejects a null Statement id in direct Role assignments")
    void shouldReturnUnprocessableContentWhenRoleStatementAssignmentContainsNullUuid() {
        // Arrange
        UUID roleId = this.api()
            .roles()
            .create(new CreateRoleRequest("null-statement-" + UUID.randomUUID(), null, Set.of()));
        String body = """
        {"statementIds":[null]}
        """;

        // Act + Assert
        assertThatThrownBy(() ->
            this.api().patchJson("/api/v0/roles/" + roleId + "/statements", body)
        ).isInstanceOfSatisfying(HttpClientErrorException.UnprocessableContent.class, exception ->
            assertThat(exception.getResponseBodyAsString())
                .contains("\"code\":\"VALIDATION_ERROR\"")
                .contains("statementIds")
        );
    }

    /**
     * Verifies that direct User-to-Statement assignment validates collection elements before application logic.
     *
     * Given: an existing User and a replacement payload containing statementIds = [null].
     * Expect: the API returns HTTP 422 and the null identifier never reaches assignment services.
     */
    @Test
    @DisplayName("rejects a null Statement id in direct User assignments")
    void shouldReturnUnprocessableContentWhenUserStatementAssignmentContainsNullUuid() {
        // Arrange
        String username = "null-statement-" + UUID.randomUUID().toString().replace("-", "");
        UUID userId = this.api()
            .users()
            .create(
                new CreateUserRequest(
                    username,
                    Set.of(username + "@example.com"),
                    "Null",
                    "Statement",
                    Set.of(),
                    Set.of()
                )
            );
        String body = """
        {"statementIds":[null]}
        """;

        // Act + Assert
        assertThatThrownBy(() ->
            this.api().patchJson("/api/v0/users/" + userId + "/statements", body)
        ).isInstanceOfSatisfying(HttpClientErrorException.UnprocessableContent.class, exception ->
            assertThat(exception.getResponseBodyAsString())
                .contains("\"code\":\"VALIDATION_ERROR\"")
                .contains("statementIds")
        );
    }

    private static Stream<Arguments> unsupportedStatementValues() {
        return Stream.of(
            Arguments.of(
                "effect",
                """
                {
                  "code": "invalid-effect",
                  "description": null,
                  "effect": "unsupported",
                  "scope": "request",
                  "target": {"api": {"method": "GET", "path": "/api/v0/users"}},
                  "policy": "return true;"
                }
                """
            ),
            Arguments.of(
                "scope",
                """
                {
                  "code": "invalid-scope",
                  "description": null,
                  "effect": "allow",
                  "scope": "unsupported",
                  "target": {"api": {"method": "GET", "path": "/api/v0/users"}},
                  "policy": "return true;"
                }
                """
            )
        );
    }

    private static Stream<Arguments> createRequestsWithNullUuidElements() {
        return Stream.of(
            Arguments.of(
                "roleIds",
                "/api/v0/roles",
                """
                {
                  "code": "invalid-role",
                  "displayName": "Invalid role",
                  "description": null,
                  "roleIds": [null]
                }
                """
            ),
            Arguments.of(
                "groupIds",
                "/api/v0/groups",
                """
                {
                  "code": "invalid-child-group",
                  "displayName": "Invalid child group",
                  "description": null,
                  "groupIds": [null],
                  "roleIds": []
                }
                """
            ),
            Arguments.of(
                "roleIds",
                "/api/v0/groups",
                """
                {
                  "code": "invalid-group-role",
                  "displayName": "Invalid group role",
                  "description": null,
                  "groupIds": [],
                  "roleIds": [null]
                }
                """
            ),
            Arguments.of(
                "roleIds",
                "/api/v0/users",
                """
                {
                  "username": "invalid-role-user",
                  "emails": ["invalid-role-user@example.com"],
                  "firstName": "Invalid",
                  "lastName": "Role",
                  "roleIds": [null],
                  "groupIds": []
                }
                """
            ),
            Arguments.of(
                "groupIds",
                "/api/v0/users",
                """
                {
                  "username": "invalid-group-user",
                  "emails": ["invalid-group-user@example.com"],
                  "firstName": "Invalid",
                  "lastName": "Group",
                  "roleIds": [],
                  "groupIds": [null]
                }
                """
            )
        );
    }
}
