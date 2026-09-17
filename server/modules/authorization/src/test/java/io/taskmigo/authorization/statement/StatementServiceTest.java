package io.taskmigo.authorization.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorization;
import io.taskmigo.authorization.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.persistence.statement.StatementEntity;
import io.taskmigo.authorization.persistence.statement.StatementRepository;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.LanguageCompiler;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class StatementServiceTest {

    private static final String VALID_POLICY = "return true;";

    private StatementRepository statements;
    private StatementService service;

    @BeforeEach
    void setUp() {
        this.statements = mock(StatementRepository.class);
        StatementPolicyValidator policyValidator = new StatementPolicyValidator(
            mock(ObjectAuthorization.class),
            new LanguageCompiler()
        );
        QueryPredicateBinder<StatementInfo, StatementEntity> queryBinder = mock(QueryPredicateBinder.class);
        ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity> objectBinder = mock(
            ObjectAuthorizationPredicateBinder.class
        );
        this.service = new StatementService(this.statements, policyValidator, queryBinder, objectBinder);
    }

    @Test
    @DisplayName("normalizes a valid statement before saving it")
    void shouldNormalizeStatementWhenInputIsValid() {
        when(this.statements.existsByName("users_read")).thenReturn(false);
        ArgumentCaptor<StatementEntity> saved = ArgumentCaptor.forClass(StatementEntity.class);

        UUID id = this.service.create(
            "users_read",
            " description ",
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            " /api/v0/users ",
            "return request.path == \"/api/v0/users\";"
        );

        assertThat(id).isNotNull();
        verify(this.statements).save(saved.capture());
        assertThat(saved.getValue().info().target().api().method()).isEqualTo("GET");
        assertThat(saved.getValue().info().name()).isEqualTo("users_read");
        assertThat(saved.getValue().info().scope()).isEqualTo(Scope.REQUEST);
        assertThat(saved.getValue().info().policy()).isEqualTo("return request.path == \"/api/v0/users\";");
    }

    @Test
    @DisplayName("rejects a statement when policy is missing or blank")
    void shouldRejectStatementWhenPolicyIsMissingOrBlank() {
        assertThatThrownBy(() -> this.create("missing-policy", null)).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> this.create("empty-policy", "")).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> this.create("blank-policy", " \t\n ")).isInstanceOf(AuthorizationException.class);
        verify(this.statements, Mockito.never()).save(ArgumentMatchers.any(StatementEntity.class));
    }

    @Test
    @DisplayName("rejects malformed policy before saving a statement")
    void shouldRejectStatementWhenPolicyCannotBeCompiled() {
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

    @Test
    @DisplayName("rejects an invalid path regular expression")
    void shouldRejectStatementWhenPathRegexIsInvalid() {
        assertThatThrownBy(() ->
            this.service.create("invalid", null, Effect.ALLOW, Scope.REQUEST, "GET", "[", VALID_POLICY)
        )
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("valid regular expression");
    }

    @Test
    @DisplayName("matches only the complete request path without its query string")
    void shouldMatchCompletePathWhenQueryStringIsPresent() {
        StatementInfo statement = new StatementInfo(
            UUID.randomUUID(),
            "users_read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            new TargetInfo(new ApiInfo("GET", "/api/v0/users/[0-9]+")),
            VALID_POLICY
        );

        StatementExecutionArtifact executable = executable(statement);
        boolean exactMatch = executable.matches("GET", "/api/v0/users/42?active=true");
        boolean suffixMatch = executable.matches("GET", "/api/v0/users/42/extra");
        boolean lowercaseMethodMatch = executable.matches("get", "/api/v0/users/42");

        assertThat(exactMatch).isTrue();
        assertThat(suffixMatch).isFalse();
        assertThat(lowercaseMethodMatch).isFalse();
    }

    @Test
    @DisplayName("matches every HTTP method when the target method is a wildcard")
    void shouldMatchEveryMethodWhenTargetMethodIsWildcard() {
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
            new StatementDefinition("users_all", null, Effect.ALLOW, Scope.REQUEST, "*", "/api/v0/users", VALID_POLICY)
        );

        StatementExecutionArtifact executable = executable(entity.info());
        boolean getMatches = executable.matches("GET", "/api/v0/users");
        boolean deleteMatches = executable.matches("DELETE", "/api/v0/users");

        assertThat(getMatches).isTrue();
        assertThat(deleteMatches).isTrue();
    }

    private UUID create(String name, @Nullable String policy) {
        return this.service.create(name, null, Effect.ALLOW, Scope.REQUEST, "GET", "/api/v0/users", policy);
    }

    private static StatementExecutionArtifact executable(StatementInfo statement) {
        return new StatementExecutionArtifact(
            statement,
            mock(CompiledSource.class),
            Pattern.compile(statement.target().api().path())
        );
    }
}
