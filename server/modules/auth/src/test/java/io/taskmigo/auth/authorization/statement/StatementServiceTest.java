package io.taskmigo.auth.authorization.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.auth.authorization.condition.AuthorizationException;
import java.util.List;
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
            TargetType.REQUEST,
            "GET",
            " /api/v0/users ",
            List.of("request.path == '/api/v0/users'")
        );

        // Assert
        assertThat(id).isNotNull();
        verify(this.statements).save(saved.capture());
        assertThat(saved.getValue().method).isEqualTo("GET");
        assertThat(saved.getValue().name).isEqualTo("users_read");
        assertThat(saved.getValue().conditions).containsExactly("request.path == '/api/v0/users'");
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
        assertThatThrownBy(() ->
            this.service.create("invalid", null, Effect.ALLOW, TargetType.REQUEST, "GET", "[", List.of())
        )
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
            new TargetInfo(TargetType.REQUEST, new ApiInfo("GET", "/api/v0/users/[0-9]+")),
            List.of()
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
        UUID id = this.service.create(
            "users_all",
            null,
            Effect.ALLOW,
            TargetType.REQUEST,
            "*",
            "/api/v0/users",
            List.of()
        );
        StatementEntity entity = new StatementEntity(
            id,
            "users_all",
            null,
            Effect.ALLOW,
            TargetType.REQUEST,
            "*",
            "/api/v0/users",
            List.of()
        );

        // Act
        boolean getMatches = entity.info().matches("GET", "/api/v0/users");
        boolean deleteMatches = entity.info().matches("DELETE", "/api/v0/users");

        // Assert
        assertThat(getMatches).isTrue();
        assertThat(deleteMatches).isTrue();
    }
}
