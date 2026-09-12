package io.taskmigo.authorization.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorization;
import io.taskmigo.language.LanguageCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementPolicyValidatorTest {

    private static final String VALID_REQUEST_POLICY = "return request.path == \"/api/v0/users\";";

    private ObjectAuthorization objectAuthorization;
    private StatementPolicyValidator validator;

    @BeforeEach
    void setUp() {
        this.objectAuthorization = mock(ObjectAuthorization.class);
        this.validator = new StatementPolicyValidator(this.objectAuthorization, new LanguageCompiler());
    }

    /**
     * Verifies that a valid definition is normalized before the persistence boundary receives it.
     *
     * Given: a request Statement with surrounding whitespace on its method and path.
     * Expect: the returned definition contains the canonical method and path while retaining the policy source.
     */
    @Test
    @DisplayName("normalizes a valid statement definition")
    void shouldNormalizeStatementWhenDefinitionIsValid() {
        // Arrange

        // Act
        StatementDefinition definition = this.validator.validate(
            "users_read",
            "description",
            Effect.ALLOW,
            Scope.REQUEST,
            " GET ",
            " /api/v0/users ",
            VALID_REQUEST_POLICY
        );

        // Assert
        assertThat(definition.name()).isEqualTo("users_read");
        assertThat(definition.method()).isEqualTo("GET");
        assertThat(definition.path()).isEqualTo("/api/v0/users");
        assertThat(definition.policy()).isEqualTo(VALID_REQUEST_POLICY);
    }

    /**
     * Verifies that missing policy source is rejected before any compiler or object validator is invoked.
     *
     * Given: a request Statement with a blank policy.
     * Expect: validation fails closed with a transport-neutral authorization error.
     */
    @Test
    @DisplayName("rejects a statement when policy is blank")
    void shouldRejectStatementWhenPolicyIsBlank() {
        // Arrange

        // Act + Assert
        assertThatThrownBy(() ->
            this.validator.validate("users_read", null, Effect.ALLOW, Scope.REQUEST, "GET", "/api/v0/users", " \t\n ")
        )
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("policy must not be blank");
    }

    /**
     * Verifies that malformed request policy source is rejected during Statement validation.
     *
     * Given: a request Statement containing malformed Embedded Language source.
     * Expect: validation fails before a Statement definition is returned.
     */
    @Test
    @DisplayName("rejects malformed request policy")
    void shouldRejectStatementWhenRequestPolicyIsMalformed() {
        // Arrange

        // Act + Assert
        assertThatThrownBy(() ->
            this.validator.validate(
                "users_read",
                null,
                Effect.ALLOW,
                Scope.REQUEST,
                "GET",
                "/api/v0/users",
                "return request.method == ;"
            )
        ).isInstanceOf(AuthorizationException.class);
    }

    /**
     * Verifies that object policy validation remains delegated to the authorization-owned object contract.
     *
     * Given: a valid object Statement definition and an object policy validator.
     * Expect: the object validator receives the normalized target and policy exactly once.
     */
    @Test
    @DisplayName("delegates object policy validation")
    void shouldDelegateObjectPolicyWhenScopeIsObject() {
        // Arrange
        String policy = "return true;";

        // Act
        StatementDefinition definition = this.validator.validate(
            "users_read",
            null,
            Effect.ALLOW,
            Scope.OBJECT,
            " GET ",
            " /api/v0/users ",
            policy
        );

        // Assert
        verify(this.objectAuthorization).validatePolicy(policy, "GET", "/api/v0/users");
        assertThat(definition.scope()).isEqualTo(Scope.OBJECT);
    }
}
