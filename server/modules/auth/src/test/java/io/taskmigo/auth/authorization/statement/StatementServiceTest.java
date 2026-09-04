package io.taskmigo.auth.authorization.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationService;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private StatementRepository statements;

    @Mock
    private JavaScriptPolicyCompiler policyCompiler;

    @InjectMocks
    private StatementService service;

    /**
     * Verifies that a valid Statement is normalized before it is persisted.
     *
     * Given: a request with a valid machine-readable name and the canonical uppercase HTTP method.
     * Expect: the saved entity has the valid name, canonical method, and a stable UUID.
     */
    @Test
    @DisplayName("normalizes a valid statement before saving it")
    void shouldNormalizeStatementWhenInputIsValid() {
        // Arrange
        when(this.statements.existsByName("users_read")).thenReturn(false);
        ArgumentCaptor<StatementEntity> saved = ArgumentCaptor.forClass(StatementEntity.class);

        // Act
        UUID id = this.service.create(
            "users_read",
            " description ",
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            " /api/v0/users ",
            "export default ({ request }) => request.path === '/api/v0/users';"
        );

        // Assert
        assertThat(id).isNotNull();
        verify(this.statements).save(saved.capture());
        assertThat(saved.getValue().method).isEqualTo("GET");
        assertThat(saved.getValue().name).isEqualTo("users_read");
        assertThat(saved.getValue().scope).isEqualTo(Scope.REQUEST);
        assertThat(saved.getValue().policy).isEqualTo(
            "export default ({ request }) => request.path === '/api/v0/users';"
        );
    }

    /**
     * Verifies that an omitted policy is persisted as a valid unconditional Statement.
     *
     * Given: a valid request Statement without a policy source.
     * Expect: the saved entity has a null policy and policy compilation is not invoked.
     */
    @Test
    @DisplayName("persists an unconditional statement when policy is omitted")
    void shouldPersistUnconditionalStatementWhenPolicyIsOmitted() {
        // Arrange
        when(this.statements.existsByName("users_all")).thenReturn(false);
        ArgumentCaptor<StatementEntity> saved = ArgumentCaptor.forClass(StatementEntity.class);

        // Act
        this.service.create("users_all", null, Effect.ALLOW, Scope.REQUEST, "GET", "/api/v0/users", null);

        // Assert
        verify(this.statements).save(saved.capture());
        assertThat(saved.getValue().policy).isNull();
        verifyNoInteractions(this.policyCompiler);
    }

    /**
     * Verifies that policy syntax is compiled during Statement activation rather than deferred to request handling.
     *
     * Given: a new request Statement containing malformed JavaScript policy source.
     * Expect: activation fails and the malformed Statement is never persisted.
     */
    @Test
    @DisplayName("rejects malformed policy before saving a statement")
    void shouldRejectStatementWhenPolicyCannotBeCompiled() {
        // Arrange
        when(this.statements.existsByName("invalid_policy")).thenReturn(false);
        StatementService activation = new StatementService(
            this.statements,
            mock(ObjectAuthorizationService.class),
            new JavaScriptPolicyCompiler()
        );

        // Act + Assert
        assertThatThrownBy(() ->
            activation.create(
                "invalid_policy",
                null,
                Effect.ALLOW,
                Scope.REQUEST,
                "GET",
                "/api/v0/users",
                "export default ({ request }) => request.method === ;"
            )
        )
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("cannot be parsed");
        verify(this.statements, never()).save(org.mockito.ArgumentMatchers.any(StatementEntity.class));
    }

    /**
     * Verifies that malformed target paths are rejected before persistence.
     *
     * Given: a Statement whose API path contains an invalid regular expression.
     * Expect: an authorization failure and no repository save.
     */
    @Test
    @DisplayName("rejects an invalid path regular expression")
    void shouldRejectStatementWhenPathRegexIsInvalid() {
        // Arrange
        when(this.statements.existsByName("invalid")).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> this.service.create("invalid", null, Effect.ALLOW, Scope.REQUEST, "GET", "[", null))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("valid regular expression");
    }

    /**
     * Verifies that Statement path matching uses full-match semantics and ignores query strings.
     *
     * Given: a GET Statement targeting `/api/v0/users/[0-9]+` and two candidate request paths.
     * Expect: the exact path matches even with a query string, while a suffix path does not.
     */
    @Test
    @DisplayName("matches only the complete request path without its query string")
    void shouldMatchCompletePathWhenQueryStringIsPresent() {
        // Arrange
        StatementInfo statement = new StatementInfo(
            UUID.randomUUID(),
            "users_read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", "/api/v0/users/[0-9]+")),
            null
        );

        // Act
        boolean exactMatch = statement.matches("GET", "/api/v0/users/42?active=true");
        boolean suffixMatch = statement.matches("GET", "/api/v0/users/42/extra");
        boolean lowercaseMethodMatch = statement.matches("get", "/api/v0/users/42");

        // Assert
        assertThat(exactMatch).isTrue();
        assertThat(suffixMatch).isFalse();
        assertThat(lowercaseMethodMatch).isFalse();
    }

    /**
     * Verifies that a wildcard method matches every HTTP method while retaining full-match path behavior.
     *
     * Given: an unconditional Statement targeting all methods at `/api/v0/users`.
     * Expect: GET and DELETE requests match the Statement.
     */
    @Test
    @DisplayName("matches every HTTP method when the target method is a wildcard")
    void shouldMatchEveryMethodWhenTargetMethodIsWildcard() {
        // Arrange
        when(this.statements.existsByName("users_all")).thenReturn(false);
        UUID id = this.service.create("users_all", null, Effect.ALLOW, Scope.REQUEST, "*", "/api/v0/users", null);
        StatementEntity entity = new StatementEntity(
            id,
            "users_all",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "*",
            "/api/v0/users",
            null
        );

        // Act
        boolean getMatches = entity.info().matches("GET", "/api/v0/users");
        boolean deleteMatches = entity.info().matches("DELETE", "/api/v0/users");

        // Assert
        assertThat(getMatches).isTrue();
        assertThat(deleteMatches).isTrue();
    }
}
