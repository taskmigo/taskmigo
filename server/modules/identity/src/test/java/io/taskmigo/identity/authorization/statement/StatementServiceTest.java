package io.taskmigo.identity.authorization.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorization;
import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistry;
import io.taskmigo.authorization.request.StatementArtifactFactory;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.StatementPolicyValidator;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.identity.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.persistence.statement.StatementEntity;
import io.taskmigo.identity.persistence.statement.StatementRepository;
import io.taskmigo.language.LanguageCompiler;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("checkstyle:UnusedPrivateField")
class StatementServiceTest {

    private static final String VALID_POLICY = "return true;";

    @Mock
    private StatementRepository statements;

    @Spy
    private StatementPolicyValidator policyValidator = new StatementPolicyValidator(
        mock(ObjectAuthorization.class),
        new LanguageCompiler()
    );

    @Mock
    private QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder;

    @Mock
    private ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder;

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
            "return request.path == \"/api/v0/users\";"
        );

        // Assert
        assertThat(id).isNotNull();
        verify(this.statements).save(saved.capture());
        assertThat(saved.getValue().info().target().api().method()).isEqualTo("GET");
        assertThat(saved.getValue().info().name()).isEqualTo("users_read");
        assertThat(saved.getValue().info().scope()).isEqualTo(Scope.REQUEST);
        assertThat(saved.getValue().info().policy()).isEqualTo("return request.path == \"/api/v0/users\";");
    }

    /**
     * Verifies that a Statement cannot be activated without a non-blank policy source.
     *
     * Given: Statements with a missing, empty, or whitespace-only policy source.
     * Expect: each activation fails with an authorization error before policy compilation or persistence.
     */
    @Test
    @DisplayName("rejects a statement when policy is missing or blank")
    void shouldRejectStatementWhenPolicyIsMissingOrBlank() {
        // Arrange
        // Act + Assert
        assertThatThrownBy(() -> this.create("missing-policy", null)).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> this.create("empty-policy", "")).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> this.create("blank-policy", " \t\n ")).isInstanceOf(AuthorizationException.class);
        verify(this.statements, Mockito.never()).save(ArgumentMatchers.any(StatementEntity.class));
    }

    /**
     * Verifies that policy syntax is compiled during Statement activation rather than deferred to request handling.
     *
     * Given: a new request Statement containing malformed Embedded Language source.
     * Expect: activation fails and the malformed Statement is never persisted.
     */
    @Test
    @DisplayName("rejects malformed policy before saving a statement")
    void shouldRejectStatementWhenPolicyCannotBeCompiled() {
        // Arrange
        // Act + Assert
        assertThatThrownBy(() ->
            this.service.create(
                "invalid_policy",
                null,
                Effect.ALLOW,
                Scope.REQUEST,
                "GET",
                "/api/v0/users",
                "return request.method == ;"
            )
        ).isInstanceOf(AuthorizationException.class);
        verify(this.statements, never()).save(ArgumentMatchers.any(StatementEntity.class));
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
        // Act + Assert
        assertThatThrownBy(() ->
            this.service.create("invalid", null, Effect.ALLOW, Scope.REQUEST, "GET", "[", VALID_POLICY)
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
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", "/api/v0/users/[0-9]+")),
            VALID_POLICY
        );

        // Act
        StatementExecutionArtifact executable = executable(statement);
        boolean exactMatch = executable.matches("GET", "/api/v0/users/42?active=true");
        boolean suffixMatch = executable.matches("GET", "/api/v0/users/42/extra");
        boolean lowercaseMethodMatch = executable.matches("get", "/api/v0/users/42");

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
            Scope.REQUEST,
            "*",
            "/api/v0/users",
            VALID_POLICY
        );
        StatementEntity entity = new StatementEntity(
            id,
            "users_all",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "*",
            "/api/v0/users",
            VALID_POLICY
        );

        // Act
        StatementExecutionArtifact executable = executable(entity.info());
        boolean getMatches = executable.matches("GET", "/api/v0/users");
        boolean deleteMatches = executable.matches("DELETE", "/api/v0/users");

        // Assert
        assertThat(getMatches).isTrue();
        assertThat(deleteMatches).isTrue();
    }

    private UUID create(String name, @Nullable String policy) {
        return this.service.create(name, null, Effect.ALLOW, Scope.REQUEST, "GET", "/api/v0/users", policy);
    }

    private static StatementExecutionArtifact executable(StatementInfo statement) {
        return new StatementArtifactFactory(
            new LanguageCompiler(),
            List.of(),
            ObjectAuthorizationSchemaRegistry.all(List.of())
        )
            .build(List.of(statement))
            .getFirst();
    }
}
