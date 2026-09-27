package io.taskmigo.authorization.statement.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementException;
import io.taskmigo.authorization.statement.application.port.in.internal.StatementCommandService;
import io.taskmigo.authorization.statement.application.port.out.StatementQueryRepository;
import io.taskmigo.authorization.statement.domain.Statement;
import io.taskmigo.authorization.statement.domain.StatementRuleViolation;
import io.taskmigo.foundation.DomainFailureType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@NullMarked
class DefaultStatementServiceTest {

    private final StatementCommandService commands = mock(StatementCommandService.class);
    private final StatementQueryRepository statements = mock(StatementQueryRepository.class);
    private final DefaultStatementService service = new DefaultStatementService(
        this.commands,
        this.statements,
        directTransactions()
    );

    /**
     * Verifies domain rule violations are translated at the published Statement application boundary.
     *
     * Given: the canonical command path rejects a duplicate Statement code.
     * Expect: the public service reports the existing transport-neutral Authorization bad-request failure.
     */
    @Test
    @DisplayName("translates statement rule violations to authorization failures")
    void shouldTranslateRuleViolationWhenRuntimeCreationFails() {
        // Arrange
        when(
            this.commands.createRuntime(
                "users_read",
                null,
                Effect.ALLOW,
                Scope.REQUEST,
                "GET",
                "/users",
                "return true;"
            )
        ).thenThrow(StatementRuleViolation.duplicateCode());

        // Act + Assert
        assertThatThrownBy(() ->
            this.service.create("users_read", null, Effect.ALLOW, Scope.REQUEST, "GET", "/users", "return true;")
        )
            .isInstanceOf(AuthorizationException.class)
            .hasMessage("Statement code already exists");
    }

    /**
     * Verifies an existing Statement is deleted through the canonical command path.
     *
     * Given: a Statement id that resolves to a canonical Statement aggregate.
     * Expect: the command service receives exactly that Statement for deletion.
     */
    @Test
    @DisplayName("deletes an existing statement through the command service")
    void shouldDeleteStatementWhenStatementExists() {
        // Arrange
        UUID id = UUID.randomUUID();
        Statement statement = mock(Statement.class);
        when(this.commands.findById(id)).thenReturn(Optional.of(statement));

        // Act
        this.service.deleteStatement(id);

        // Assert
        verify(this.commands).delete(statement);
    }

    /**
     * Verifies missing Statement deletion is reported with the domain not-found category.
     *
     * Given: a Statement id that cannot be resolved by the canonical command path.
     * Expect: deletion fails as NOT_FOUND with the stable Statement-not-found message.
     */
    @Test
    @DisplayName("reports not found when deleting a missing statement")
    void shouldReportNotFoundWhenStatementDoesNotExist() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(this.commands.findById(id)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> this.service.deleteStatement(id)).isInstanceOfSatisfying(
            StatementException.class,
            exception -> {
                assertThat(exception.type()).isEqualTo(DomainFailureType.NOT_FOUND);
                assertThat(exception.getMessage()).isEqualTo("Statement not found");
            }
        );
    }

    /**
     * Verifies Statement existence checks use the read-side repository rather than the mutation aggregate port.
     *
     * Given: a requested Statement id that the projection repository cannot resolve.
     * Expect: role/grant validation receives the existing authorization bad-request failure.
     */
    @Test
    @DisplayName("rejects missing statement ids through query repository")
    void shouldRejectRequiredStatementsWhenQueryRepositoryIsIncomplete() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(this.statements.containsAll(Set.of(id))).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> this.service.requireStatements(Set.of(id)))
            .isInstanceOf(AuthorizationException.class)
            .hasMessage("One or more Statements do not exist");
    }

    private static TransactionRunner directTransactions() {
        return new TransactionRunner() {
            @Override
            public <T> T read(Supplier<T> work) {
                return work.get();
            }

            @Override
            public <T> T write(Supplier<T> work) {
                return work.get();
            }

            @Override
            public void write(Runnable work) {
                work.run();
            }
        };
    }
}
