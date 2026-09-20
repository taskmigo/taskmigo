package io.taskmigo.authorization.statement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementTest {

    /**
     * Verifies that Statement creation enforces structural constraints without compiling policy or target regex.
     *
     * Given: a valid code with a malformed regex target and syntactically invalid policy source.
     * Expect: the canonical aggregate is created and retains both values for runtime semantic validation.
     */
    @Test
    @DisplayName("accepts semantically invalid target and policy during structural creation")
    void shouldAcceptSemanticInvalidityWhenStatementIsStructurallyValid() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        Statement statement = Statement.create(
            id,
            "users_read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            " GET ",
            " [ ",
            "return request.method == ;"
        );

        // Assert
        assertThat(statement.id()).isEqualTo(id);
        assertThat(statement.target()).isEqualTo(new StatementTarget("GET", "["));
        assertThat(statement.policy().source()).isEqualTo("return request.method == ;");
    }

    /**
     * Verifies that policy remains a required part of the canonical Statement structure.
     *
     * Given: an otherwise valid Statement whose policy contains only whitespace.
     * Expect: aggregate creation fails with a structural rule violation before persistence.
     */
    @Test
    @DisplayName("rejects a structurally blank policy")
    void shouldRejectStatementWhenPolicyIsBlank() {
        // Arrange

        // Act + Assert
        assertThatThrownBy(() ->
            Statement.create(
                UUID.randomUUID(),
                "users_read",
                null,
                Effect.ALLOW,
                Scope.REQUEST,
                "GET",
                "/users",
                "  "
            )
        )
            .isInstanceOf(StatementRuleViolation.class)
            .hasMessageContaining("policy");
    }

    /**
     * Verifies that Statement code format remains a canonical identity invariant.
     *
     * Given: a Statement code shorter than the confirmed six-character minimum.
     * Expect: aggregate creation rejects the invalid stable identity.
     */
    @Test
    @DisplayName("rejects an invalid statement code")
    void shouldRejectStatementWhenCodeFormatIsInvalid() {
        // Arrange

        // Act + Assert
        assertThatThrownBy(() ->
            Statement.create(
                UUID.randomUUID(),
                "short",
                null,
                Effect.ALLOW,
                Scope.REQUEST,
                "GET",
                "/users",
                "return true;"
            )
        )
            .isInstanceOf(StatementRuleViolation.class)
            .hasMessageContaining("[a-zA-Z0-9_-]{6,255}");
    }

    /**
     * Verifies that managed reconciliation mutates policy state without changing Statement identity.
     *
     * Given: a restored Statement and a changed description, effect, scope, target, and policy.
     * Expect: reconciliation reports a change while preserving the original id and code.
     */
    @Test
    @DisplayName("reconciles mutable statement state while preserving identity")
    void shouldPreserveIdentityWhenManagedStateChanges() {
        // Arrange
        UUID id = UUID.randomUUID();
        Statement statement = Statement.restore(
            id,
            "users_read",
            "before",
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/users",
            "return true;"
        );

        // Act
        boolean changed = statement.reconcile(
            "after",
            Effect.DENY,
            Scope.OBJECT,
            "POST",
            "/users/.*",
            "return object.id != null;"
        );

        // Assert
        assertThat(changed).isTrue();
        assertThat(statement.id()).isEqualTo(id);
        assertThat(statement.code().value()).isEqualTo("users_read");
        assertThat(statement.description()).isEqualTo("after");
        assertThat(statement.effect()).isEqualTo(Effect.DENY);
        assertThat(statement.scope()).isEqualTo(Scope.OBJECT);
    }
}
