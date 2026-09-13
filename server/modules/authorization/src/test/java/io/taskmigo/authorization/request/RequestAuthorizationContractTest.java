package io.taskmigo.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.spi.EffectiveStatementResolver;
import io.taskmigo.authorization.spi.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.language.LanguageCompiler;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RequestAuthorizationContractTest {

    private final EffectiveStatementResolver statements = mock(EffectiveStatementResolver.class);
    private final RequestAuthorizationService service = new RequestAuthorizationService(
        this.statements,
        new StatementArtifactFactory(
            new LanguageCompiler(),
            List.of(),
            ObjectAuthorizationTargetResolver.all(List.of())
        )
    );

    /**
     * Verifies that the typed Request Authorization API maps principal and request inputs into policy roots and returns
     * the expected grant decision.
     *
     * Given: representative typed principals and requests covering method, username, path-variable, and deny-override
     * policies.
     * Expect: each authorization result has the explicitly declared granted or denied outcome for that input case.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("requestCases")
    @DisplayName("returns the expected decision for typed request inputs")
    void shouldReturnExpectedDecisionWhenTypedRequestInputIsProvided(RequestCase testCase) {
        // Arrange
        when(this.statements.resolve(testCase.principal().id())).thenReturn(testCase.statements());

        // Act
        RequestAuthorizationResult result = this.service.authorize(testCase.principal(), testCase.request());

        // Assert
        assertThat(result.granted()).isEqualTo(testCase.expectedGranted());
    }

    private static Stream<RequestCase> requestCases() {
        return Stream.of(
            requestCase(
                "allows when request method satisfies policy",
                principal("alice"),
                request("GET", "/api/v0/users"),
                List.of(statement(Effect.ALLOW, "return request.method == \"GET\";")),
                true
            ),
            requestCase(
                "denies when request method does not satisfy policy",
                principal("alice"),
                request("POST", "/api/v0/users"),
                List.of(statement(Effect.ALLOW, "return request.method == \"GET\";")),
                false
            ),
            requestCase(
                "allows when principal username satisfies policy",
                principal("alice"),
                request("GET", "/api/v0/users"),
                List.of(statement(Effect.ALLOW, "return principal.username == \"alice\";")),
                true
            ),
            requestCase(
                "allows when request path variable satisfies policy",
                principal("alice"),
                new AuthorizationRequest("GET", "/api/v0/users/42", Map.of("userId", "42")),
                List.of(
                    statement(Effect.ALLOW, "*", "/api/v0/users/.*", "return request.pathVariables.userId == \"42\";")
                ),
                true
            ),
            requestCase(
                "denies when request path variable does not satisfy policy",
                principal("alice"),
                new AuthorizationRequest("GET", "/api/v0/users/41", Map.of("userId", "41")),
                List.of(
                    statement(Effect.ALLOW, "*", "/api/v0/users/.*", "return request.pathVariables.userId == \"42\";")
                ),
                false
            ),
            requestCase(
                "denies when matching deny overrides matching allow",
                principal("blocked"),
                request("GET", "/api/v0/users"),
                List.of(
                    statement(Effect.ALLOW, "return true;"),
                    statement(Effect.DENY, "return principal.username == \"blocked\";")
                ),
                false
            )
        );
    }

    private static RequestCase requestCase(
        String name,
        AuthorizationPrincipal principal,
        AuthorizationRequest request,
        List<EffectiveStatement> statements,
        boolean expectedGranted
    ) {
        return new RequestCase(name, principal, request, statements, expectedGranted);
    }

    private static AuthorizationPrincipal principal(String username) {
        UUID id = UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8));
        return new AuthorizationPrincipal(id, username);
    }

    private static AuthorizationRequest request(String method, String path) {
        return new AuthorizationRequest(method, path, Map.of());
    }

    private static EffectiveStatement statement(Effect effect, String policy) {
        return statement(effect, "*", "/api/v0/users", policy);
    }

    private static EffectiveStatement statement(Effect effect, String method, String path, String policy) {
        StatementInfo statement = new StatementInfo(
            UUID.randomUUID(),
            "statement-" + UUID.randomUUID(),
            null,
            effect,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo(method, path)),
            policy
        );
        return new EffectiveStatement(statement, Instant.EPOCH);
    }

    private record RequestCase(
        String name,
        AuthorizationPrincipal principal,
        AuthorizationRequest request,
        List<EffectiveStatement> statements,
        boolean expectedGranted
    ) {
        @Override
        public String toString() {
            return this.name;
        }
    }
}
