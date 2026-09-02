package com.workastra.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for {@link ExampleController}.
 *
 * Scope: boots the full Spring context against a real PostgreSQL instance
 * (via Testcontainers) and exercises the HTTP layer end-to-end — controller,
 * service, repository, and DB constraints/migrations all run for real. Use
 * this template when a test needs to verify wiring across layers, DB
 * behavior (constraints, triggers, Liquibase changesets), or actual HTTP
 * request/response handling that mocks can't faithfully represent.
 *
 * Replace ExampleController and the request/response shapes with the real
 * ones under test. @ServiceConnection auto-wires the datasource from the
 * container — no manual @DynamicPropertySource needed.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExampleControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Verifies the full request-to-persistence path for creating an example,
     * including the Liquibase-managed schema and any DB-level constraints or
     * triggers — not just that the controller returns the right status code.
     *
     * Given: POST /examples with body {"name": "integration-test-example"}.
     * Expect: HTTP 201 Created, and the JSON response body has
     * name = "integration-test-example" (i.e. the row was actually written
     * and read back from the real database, not mocked).
     */
    @Test
    @DisplayName("should return 201 and persist example when POST /examples receives valid payload")
    void shouldReturn201AndPersistExampleWhenPostReceivesValidPayload() {
        // Arrange
        var payload = """
                { "name": "integration-test-example" }
                """;

        // Act
        var response = webTestClient.post()
                .uri("/examples")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange();

        // Assert
        response.expectStatus().isEqualTo(HttpStatus.CREATED)
                .expectBody()
                .jsonPath("$.name").isEqualTo("integration-test-example");
    }

    /**
     * Verifies the full error-handling path — an exception thrown by the
     * service layer when an example is not found is correctly translated
     * into an HTTP 404 by the controller advice, exercised through the real
     * HTTP layer rather than checked in isolation.
     *
     * Given: GET /examples/999999, an id that does not exist in the DB.
     * Expect: HTTP 404 Not Found.
     */
    @Test
    @DisplayName("should return 404 when example does not exist")
    void shouldReturn404WhenExampleDoesNotExist() {
        // Arrange
        long nonExistentId = 999_999L;

        // Act
        var response = webTestClient.get()
                .uri("/examples/{id}", nonExistentId)
                .exchange();

        // Assert
        response.expectStatus().isNotFound();
    }
}
