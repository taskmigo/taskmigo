package io.taskmigo.authorization.statement.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.application.port.in.internal.StatementMutationResult;
import io.taskmigo.authorization.statement.application.port.out.StatementCommandRepository;
import io.taskmigo.authorization.statement.domain.Statement;
import io.taskmigo.authorization.statement.domain.StatementCode;
import io.taskmigo.authorization.statement.domain.StatementRuleViolation;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class DefaultStatementCommandServiceTest {

    private final StatementCommandRepository statements = mock(StatementCommandRepository.class);
    private final DefaultStatementCommandService service = new DefaultStatementCommandService(this.statements);

    /**
     * Verifies runtime creation persists through the canonical aggregate without semantic policy compilation.
     *
     * Given: a structurally valid Statement with malformed policy syntax and a malformed target regex.
     * Expect: the command service persists the aggregate because semantic validation belongs to authorization runtime.
     */
    @Test
    @DisplayName("persists structurally valid runtime statements without semantic validation")
    void shouldPersistStatementWhenSemanticValidationIsDeferred() {
        // Arrange
        when(this.statements.findByCode(StatementCode.of("users_read"))).thenReturn(Optional.empty());
        ArgumentCaptor<Statement> saved = ArgumentCaptor.forClass(Statement.class);

        // Act
        UUID id = this.service.createRuntime(
            "users_read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "[",
            "return request.method == ;"
        );

        // Assert
        verify(this.statements).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(id);
        assertThat(saved.getValue().target().path()).isEqualTo("[");
        assertThat(saved.getValue().policy().source()).isEqualTo("return request.method == ;");
    }

    /**
     * Verifies runtime creation preserves the uniqueness invariant on stable Statement codes.
     *
     * Given: a canonical Statement code that already exists in the command repository.
     * Expect: creation fails before a second aggregate is persisted.
     */
    @Test
    @DisplayName("rejects duplicate runtime statement codes")
    void shouldRejectRuntimeCreateWhenStatementCodeAlreadyExists() {
        // Arrange
        Statement existing = statement("users_read");
        when(this.statements.findByCode(StatementCode.of("users_read"))).thenReturn(Optional.of(existing));

        // Act + Assert
        assertThatThrownBy(() ->
            this.service.createRuntime("users_read", null, Effect.ALLOW, Scope.REQUEST, "GET", "/users", "return true;")
        )
            .isInstanceOf(StatementRuleViolation.class)
            .hasMessage("Statement code already exists");
        verify(this.statements, never()).save(ArgumentMatchers.any());
    }

    /**
     * Verifies managed reconciliation updates the same canonical aggregate used by runtime creation.
     *
     * Given: an existing Statement with the requested code and changed managed fields.
     * Expect: the existing id is retained, the aggregate is saved, and the mutation reports changed.
     */
    @Test
    @DisplayName("updates existing managed statement through canonical command path")
    void shouldUpdateExistingStatementWhenManagedStateChanges() {
        // Arrange
        Statement existing = statement("users_read");
        when(this.statements.findByCode(StatementCode.of("users_read"))).thenReturn(Optional.of(existing));

        // Act
        StatementMutationResult result = this.service.reconcileManaged(
            "users_read",
            "changed",
            Effect.DENY,
            Scope.REQUEST,
            "POST",
            "/users",
            "return false;"
        );

        // Assert
        assertThat(result).isEqualTo(new StatementMutationResult(existing.id(), false, true));
        verify(this.statements).save(existing);
    }

    /**
     * Verifies managed reconciliation avoids a persistence write when canonical state is already identical.
     *
     * Given: an existing Statement whose managed fields exactly match the requested state.
     * Expect: the mutation reports unchanged and the repository is not asked to save the aggregate.
     */
    @Test
    @DisplayName("skips persistence when managed statement state is unchanged")
    void shouldSkipSaveWhenManagedStatementStateIsUnchanged() {
        // Arrange
        Statement existing = statement("users_read");
        when(this.statements.findByCode(StatementCode.of("users_read"))).thenReturn(Optional.of(existing));

        // Act
        StatementMutationResult result = this.service.reconcileManaged(
            "users_read",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/users",
            "return true;"
        );

        // Assert
        assertThat(result).isEqualTo(new StatementMutationResult(existing.id(), false, false));
        verify(this.statements, never()).save(existing);
    }

    private static Statement statement(String code) {
        return Statement.restore(
            UUID.randomUUID(),
            code,
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/users",
            "return true;"
        );
    }
}
