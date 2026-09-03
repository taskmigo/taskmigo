package io.taskmigo.rest.api.v0.testing;

import io.taskmigo.PostgresTestConfiguration;
import java.net.URI;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;

/// Supplies a running, authenticated Taskmigo API server to HTTP integration tests.
///
/// The client is cached for each JUnit test instance, so a scenario receives one real OAuth access token while
/// exercising the production HTTP security filter chain.
@NullMarked
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
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public abstract class ApiIntegrationTestSupport {

    @LocalServerPort
    private int port;

    private @Nullable TaskmigoApiClient api;

    /// Returns the authenticated client for the current integration-test server.
    protected final TaskmigoApiClient api() {
        TaskmigoApiClient existing = this.api;
        if (existing != null) return existing;

        TaskmigoApiClient created = new TaskmigoApiClient(
            URI.create("http://localhost:" + this.port),
            new TaskmigoApiClient.ClientCredentials("integration-client", "integration-secret", "taskmigo.api")
        );
        this.api = created;
        return created;
    }
}
