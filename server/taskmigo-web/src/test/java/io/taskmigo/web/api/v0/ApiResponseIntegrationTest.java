package io.taskmigo.web.api.v0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-id=integration-client",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-secret=integration-secret",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-authentication-methods=client_secret_basic",
        "spring.security.oauth2.authorizationserver.client.cli.registration.authorization-grant-types=client_credentials",
        "spring.security.oauth2.authorizationserver.client.cli.registration.scopes=taskmigo.api",
        "taskmigo.security.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
class ApiResponseIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void successfulResponsesUseTheV0Envelope() {
        String response = Objects.requireNonNull(
            this.api().get().uri("/api/v0/permissions").retrieve().body(String.class)
        );

        assertThat(response)
            .contains("\"success\":true")
            .contains("\"status_code\":200")
            .contains("\"message\":{")
            .contains("\"code\":\"resource.permissions.retrieved\"")
            .contains("\"error\":null")
            .contains("\"meta\":{")
            .contains("\"execution\":{")
            .contains("\"started_at\":")
            .contains("\"duration_ms\":")
            .contains("\"pagination\":null")
            .contains("\"data\":[");
    }

    @Test
    void createdResponsesKeepHttpSemanticsInsideTheEnvelope() {
        String key = "api-response-" + UUID.randomUUID();
        var response = this.api()
            .post()
            .uri("/api/v0/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("key", key, "name", "API Response Test"))
            .retrieve()
            .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getBody())
            .contains("\"success\":true")
            .contains("\"status_code\":201")
            .contains("\"code\":\"resource.organization.created\"")
            .contains("\"error\":null")
            .contains("\"id\":");
    }

    @Test
    void validationErrorsUse422AndFormErrors() {
        assertThatThrownBy(() ->
            this.api()
                .post()
                .uri("/api/v0/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("key", "", "name", ""))
                .retrieve()
                .body(String.class)
        ).isInstanceOfSatisfying(HttpClientErrorException.class, exception -> {
            assertThat(exception.getStatusCode().value()).isEqualTo(422);
            assertThat(exception.getResponseBodyAsString())
                .contains("\"success\":false")
                .contains("\"status_code\":422")
                .contains("\"code\":\"validation.failed\"")
                .contains("\"code\":\"VALIDATION_ERROR\"")
                .contains("\"form_errors\":{")
                .contains("\"key\":")
                .contains("\"name\":")
                .contains("\"data\":null");
        });
    }

    @Test
    void authenticationErrorsUseTheSameV0Envelope() {
        assertThatThrownBy(() ->
            this.http().get().uri("/api/v0/permissions").retrieve().body(String.class)
        ).isInstanceOfSatisfying(HttpClientErrorException.Unauthorized.class, exception ->
            assertThat(exception.getResponseBodyAsString())
                .contains("\"success\":false")
                .contains("\"status_code\":401")
                .contains("\"code\":\"security.unauthorized\"")
                .contains("\"code\":\"UNAUTHORIZED\"")
                .contains("\"pagination\":null")
                .contains("\"data\":null")
        );
    }

    private RestClient api() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + this.port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.accessToken())
            .build();
    }

    private String accessToken() {
        TokenResponse response = Objects.requireNonNull(
            this.http()
                .post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("integration-client", "integration-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&scope=taskmigo.api")
                .retrieve()
                .body(TokenResponse.class)
        );
        return response.access_token();
    }

    private RestClient http() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + this.port)
            .build();
    }

    private record TokenResponse(String access_token) {}
}
